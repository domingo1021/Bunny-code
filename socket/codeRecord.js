require('dotenv').config();
const timeDB = require('../utils/timeSeriesDB');

const { INFLUX_ORG, INFLUX_BUCKET } = process.env;
const KEY_MANAGE = ['enter', 'up', 'down'];

const writeRecord = async (userID, projectID, batchData) => {
  const writeApi = timeDB.getWriteApi(INFLUX_ORG, INFLUX_BUCKET, 'ns');
  const points = batchData.map((data) => {
    if (KEY_MANAGE.includes(data.action)) {
      return `${userID},project=${projectID},action="${data.action}",line=${data.line} code="" ${data.timestamp}`;
    }
    return `${userID},project=${projectID},action="${data.action}",line=${data.line},index=${data.index} code="${data.code}"  ${data.timestamp}`;
  });
  writeApi.writeRecords(points);

  let response;
  try {
    await writeApi.close();
    response = 'success';
  } catch (error) {
    response = 'failed';
  }
  return response;

  // TODO: write data into influx db
  // TODO: choose: batch or separately
  // TODO: send to MySQL db (start time, end time)
  // Note: 使用者每按一次 save 都是在一個新的 MySQL record
};

const queryRecord = async (userID, projectID, startTime, endTime) => {
  // Flux query
  const queryApi = timeDB.getQueryApi(INFLUX_ORG);

  // filter
  const query = `from(bucket: "bunny")
                  |> range(start: 0)
                  |> filter(fn: (r) => r["_measurement"] == "${userID}")
                `;
  return new Promise((resolve, reject) => {
    const responseData = [];
    queryApi.queryLines;
    queryApi.queryRows(query, {
      next(row, tableMeta) {
        const responseRow = tableMeta.toObject(row);
        const tmpCobject = {
          userID: responseRow._measurement,
          project: responseRow.project,
          action: responseRow.action,
          index: responseRow.index,
          line: responseRow.line,
          code: responseRow._value,
          timestamp: responseRow._time,

        };
        responseData.push(tmpCobject);
      },
      error(error) {
        reject('error occur');
      },
      complete() {
        resolve(responseData);
      },
    });
  });
};

module.exports = { writeRecord, queryRecord };
