require('dotenv').config();
const { Point } = require('@influxdata/influxdb-client');
const influxClient = require('../utils/timeSeriesDB');

const { INFLUX_ORG, INFLUX_BUCKET } = process.env;

// Write data, point in as single data record
// In a series, each point has a unique timestamp.

const writeApi = influxClient.getWriteApi(INFLUX_ORG, INFLUX_BUCKET, 'ns');
writeApi.useDefaultTags({ host: 'host1' });
writeApi.writeRecord(`user,project=1,action="create",line=1,index=2 code="e" ${new Date().getTime()}000000`);

// // 使用 Point 製造 InfluxDB line protocal，並寫入 with timestamp， 記得用 'ns'
const point = new Point('mem').tag('host_id', '12345').floatField('used_percent', 666).timestamp(`${new Date().getTime()}000000`);
const point2 = new Point('men').tag('host_id', '12345').floatField('used_12345', 666).timestamp(`${new Date().getTime()}000000`);
writeApi.writePoints([point, point2]);

writeApi
  .close()
  .then(() => {
    console.log('FINISHED');
  })
  .catch((e) => {
    console.error(e);
    console.log('Finished ERROR');
  });

// Flux query
const queryApi = influxClient.getQueryApi(INFLUX_ORG);

// // filter
const query = `from(bucket: "bunny")
                |> range(start: 0)
                |> filter(fn: (r) => r["_measurement"] == "1")
              `;
// _measurement: userID
// project: projectID
// action: create/delete/...
// index: keyword index
// _field: code
// _value: inputed code
// _time: '2022-09-02T04:25:32.47Z',

queryApi.queryRows(query, {
  next(row, tableMeta) {
    const o = tableMeta.toObject(row);
    console.log(o);
    console.log(`${o._time} ${o._measurement}: host=${o.project} ${o._field}=${o._value}`);
  },
  error(error) {
    console.error(error);
    console.log('Finished ERROR');
  },
  complete() {
    console.log('Finished SUCCESS');
  },
});
