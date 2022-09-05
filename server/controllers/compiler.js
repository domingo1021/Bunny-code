require('dotenv').config();
const { exec } = require('child_process');
const fs = require('fs');
const Compiler = require('../models/compiler');
const pool = require('../../utils/rmdb');
const timeDB = require('../../utils/timeSeriesDB');

const { INFLUX_ORG, INFLUX_BUCKET } = process.env;
const KEY_MANAGE = ['up', 'down'];

const writeFile = async (req, res) => {
  // store S3 result & related info into MySQL DB
  const { versionID } = req.body;
  const { s3Results, filenames, log } = req;
  const connection = await pool.getConnection();
  try {
    await Promise.all(s3Results.map(async (result, index) => {
      await Compiler.writeFile(filenames[index], result.key, log, versionID);
    }));
  } catch (error) {
    connection.release();
    console.log(error);
    return res.send('error occur');
  }
  return res.send('success');
};

const writeRecord = async (req, res) => {
  // check timestamp and datetime transformation. --> checked 是一樣的
  // console.log(data.timestamp, new Date(+data.timestamp.substring(0, 13)));
  // TODO: insert into S3 storage with file snapshot.
  // TODO: insert into mysql database with specific datetime （start & end).
  const {
    userID, projectID, versionID, fileName, checkpointNumber,
  } = req.body;
  const batchData = JSON.parse(req.body.batchData);
  console.log(batchData);
  const writeApi = timeDB.getWriteApi(INFLUX_ORG, INFLUX_BUCKET, 'ns');
  const points = batchData.map((data) => {
    if (KEY_MANAGE.includes(data.action)) {
      return `${userID},project=${projectID},version=${versionID},file=${fileName},checkpoint=${checkpointNumber},action=${data.action},line=${data.line} code="" ${data.timestamp}`;
    }
    return `${userID},project=${projectID},version=${versionID},file=${fileName},checkoutpoint=${checkpointNumber},action=${data.action},line=${data.line},index=${data.index} code="${data.code}"  ${data.timestamp}`;
  });

  writeApi.writeRecords(points);

  const startTime = new Date(+batchData[0].timestamp.substring(0, 13));
  const endTime = new Date(+batchData[batchData.length - 1].timestamp.substring(0, 13));

  await Compiler.writeRecord(versionID, startTime, endTime);

  let response;
  try {
    await writeApi.close();
    response = 'success';
  } catch (error) {
    response = 'failed';
  }
  return res.status(200).send(response);

  // TODO: write data into influx db
  // TODO: choose: batch or separately
  // TODO: send to MySQL db (start time, end time)
  // Note: 使用者每按一次 save 都是在一個新的 MySQL record
};

const queryRecord = async (req, res) => {
  // TODO: get time from MySQL DB.
  const { userID } = req.params;
  const {
    projectID, startTime, stopTime,
  } = req.body;
  // const userID = 1;
  // const projectID = 1;
  // const startTime = '2022-09-01T04:25:32.985Z';
  // const stopTime = '2022-10-30T04:25:32.47Z';
  // Flux query
  const queryApi = timeDB.getQueryApi(INFLUX_ORG);
  console.log(userID, projectID, startTime, stopTime);

  // filter
  const query = `from(bucket: "bunny")
                  |> range(start: ${startTime}, stop: ${stopTime})
                  |> group(columns: ["_measurement"])
                  |> filter(fn: (r) => r["_measurement"] == "${userID}")
                  |> filter(fn: (r) => r["project"] == "${projectID}")
                  |> sort(columns: ["_time"])
                `;

  const queryResponse = await new Promise((resolve, reject) => {
    const responseData = [];
    // queryApi.queryLines;
    queryApi.queryRows(query, {
      next(row, tableMeta) {
        const responseRow = tableMeta.toObject(row);
        const tmpCobject = {
          userID: responseRow._measurement,
          project: responseRow.project,
          version: responseRow.version,
          file: responseRow.file,
          checkpoint: responseRow.checkpoint,
          action: responseRow.action,
          index: responseRow.index,
          line: responseRow.line,
          code: responseRow._value,
          timestamp: responseRow._time,

        };
        responseData.push(tmpCobject);
      },
      error(error) {
        console.log(error);
        reject('error occur');
      },
      complete() {
        resolve(responseData);
      },
    });
  }).catch((error) => {
    console.log(error);
    return res.status(500).send('erorr occur');
  });
  return res.status(200).json({ data: queryResponse });
};

const runCompiler = async (req, res) => {
  const { userID } = req.body;
  const { codes } = req.body;
  async function runCommand(cmd) {
    return new Promise((resolve, reject) => {
      exec(cmd, (error, stdout, stderr) => {
        if (error) {
          console.log(`error: ${error.message}`);
          reject(error);
        }
        if (stderr) {
          console.log(`stderr: ${stderr}`);
          reject(stderr);
        }
        if (stdout) {
          console.log(`stdout: ${stdout}`);
          resolve(stdout);
        }
      });
    });
  }
  const userCodeRoute = `./user_tmp_codes/${userID}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  const compilerResult = await runCommand(`docker run -e CODE_FILE=/app/user_tmp_codes/${userID}.js -v \$\(pwd\)/user_tmp_codes:/app/user_tmp_codes --rm node-tool`);
  fs.rmSync(userCodeRoute);
  return res.status(200).json(compilerResult);
};

module.exports = {
  runCompiler, writeFile, writeRecord, queryRecord,
};
