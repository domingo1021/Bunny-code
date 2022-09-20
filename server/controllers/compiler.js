require('dotenv').config();
const Compiler = require('../models/compiler');
const Battle = require('../models/battle');
const pool = require('../../utils/rmdb');
const timeDB = require('../../utils/timeSeriesDB');
const { compile } = require('../services/service');

const { INFLUX_ORG, INFLUX_BUCKET } = process.env;
const KEY_MANAGE = ['up', 'down'];

const writeBattleFile = async (req, res) => {
  const { battleID } = req.body;
  const { s3Results } = req;
  try {
    await Battle.writeBattleFile(battleID, `/${s3Results[0].key}`);
  } catch (error) {
    console.log('write battle file error: ', error);
    return res.status(500).json({ msg: 'Write battle into database error, please upload again' });
  }
  return res.status(200).send({ data: 'File uploaded !' });
};

const getFiles = async (req, res) => {
  const { versionID } = req.body;
  const files = await Compiler.getFiles(versionID);
  return res.status(200).json({
    data: files,
  });
};

const writeFile = async (req, res) => {
  // store S3 result & related info into MySQL DB
  const { versionID } = req.body;
  const { s3Results, filenames, log } = req;
  const connection = await pool.getConnection();
  try {
    await Promise.all(s3Results.map(async (result, index) => {
      await Compiler.writeFile(filenames[index], `/${result.key}`, log, versionID);
    }));
  } catch (error) {
    connection.release();
    console.log(error);
    return res.status(500).send('error occur');
  }
  return res.status(201).json({ data: `${process.env.AWS_DISTRIBUTION_NAME}/${s3Results[0].key}` });
  // return res.send('success');
};

const writeRecord = async (req, res) => {
  // check timestamp and datetime transformation. --> checked 是一樣的
  // console.log(data.timestamp, new Date(+data.timestamp.substring(0, 13)));
  // TODO: insert into S3 storage with file snapshot.
  // TODO: insert into mysql database with specific datetime （start & end).
  const {
    projectID, baseURL, versionID, fileID,
  } = req.body;
  console.log(projectID, baseURL, versionID, fileID);
  const batchData = JSON.parse(req.body.batchData);
  const startTime = new Date(+batchData[0].timestamp.substring(0, 13) - 1000000);
  const endTime = new Date(+batchData[batchData.length - 1].timestamp.substring(0, 13) + 1000000);
  let recordID;
  try {
    recordID = await Compiler.writeRecord(versionID, baseURL, startTime, endTime);
  } catch (error) {
    console.log('Write record excpetion: ', error.msg);
    return res.status(error.status).json({ msg: error.msg });
  }

  const writeApi = timeDB.getWriteApi(INFLUX_ORG, INFLUX_BUCKET, 'ns');
  const points = batchData.map((data) => {
    // if (KEY_MANAGE.includes(data.action)) {
    //   return `${projectID},version=${versionID},file=${fileID},action=${data.action},line=${data.line} code="" ${data.timestamp}`;
    // }
    if (data.code === '"') {
      return `${projectID},version=${versionID},file=${fileID},action=${data.action},line=${data.line},index=${data.index} code=""""  ${data.timestamp}`;
    }
    return `${projectID},version=${versionID},file=${fileID},action=${data.action},line=${data.line},index=${data.index} code="${data.code}"  ${data.timestamp}`;
  });

  writeApi.writeRecords(points);

  try {
    await writeApi.close();
    return res.status(200).json({
      recordID,
      versionID,
      startTime,
      baseURL,
      endTime,
    });
  } catch (error) {
    return res.status(500).json({ msg: 'Time series record failed' });
  }
  // retrun coressponding datetime (start and end), frontend 會直接 Flush 之前的記錄，MySQL 之前的 record 紀錄也都放成 deleted.
  // TODO: write data into influx db
  // TODO: choose: batch or separately
  // TODO: send to MySQL db (start time, end time)
  // Note: 使用者每按一次 save 都是在一個新的 MySQL record
};

const queryRecord = async (req, res) => {
  // TODO: get time from MySQL DB.
  const { projectID } = req.params;
  const {
    versionID, startTime, stopTime,
  } = req.body;
  // const userID = 1;
  // const projectID = 1;
  // const startTime = '2022-09-01T04:25:32.985Z';
  // const stopTime = '2022-10-30T04:25:32.47Z';
  // Flux query
  const queryApi = timeDB.getQueryApi(INFLUX_ORG);
  console.log(projectID, versionID, startTime, stopTime);

  // filter
  const query = `from(bucket: "bunny")
                  |> range(start: ${startTime}, stop: ${stopTime})
                  |> group(columns: ["_measurement"])
                  |> filter(fn: (r) => r["_measurement"] == "${projectID}")
                  |> filter(fn: (r) => r["version"] == "${versionID}")
                  |> sort(columns: ["_time"])
                `;

  const queryResponse = await new Promise((resolve, reject) => {
    const responseData = [];
    // queryApi.queryLines;
    queryApi.queryRows(query, {
      next(row, tableMeta) {
        const responseRow = tableMeta.toObject(row);
        const tmpCobject = {
          project: responseRow._measurement,
          version: responseRow.version,
          file: responseRow.file,
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
  const { codes, fileName } = req.body;
  const compilerResult = await compile(userID, fileName, codes);
  return res.status(200).json(compilerResult);
};

module.exports = {
  runCompiler, getFiles, writeFile, writeRecord, queryRecord, writeBattleFile,
};
