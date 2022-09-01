require('dotenv').config();
const { InfluxDB, WritePrecision, Point } = require('@influxdata/influxdb-client');

// You can generate an API token from the "API Tokens Tab" in the UI
const {
  INFLUX_URL, INFLUX_TOKEN, INFLUX_ORG, INFLUX_BUCKET,
} = process.env;

console.log(INFLUX_URL, INFLUX_TOKEN, INFLUX_ORG, INFLUX_BUCKET);

const client = new InfluxDB({ url: INFLUX_URL, token: INFLUX_TOKEN });

// Write data, point in as single data record
// In a series, each point has a unique timestamp.

const writeApi = client.getWriteApi(INFLUX_ORG, INFLUX_BUCKET, 'ns');
writeApi.useDefaultTags({ host: 'host1' });

// 使用 Point 製造 InfluxDB line protocal，並寫入 with timestamp， 記得用 'ns'
const point = new Point('mem').tag('host_id', '12345').floatField('used_percent', 666).timestamp(`${new Date().getTime()}000000`);
writeApi.writePoint(point);

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
const queryApi = client.getQueryApi(INFLUX_ORG);

const query = 'from(bucket: "bunny") |> range(start: 0)';
queryApi.queryRows(query, {
  next(row, tableMeta) {
    const o = tableMeta.toObject(row);
    // console.log(o);
    console.log(`${o._time} ${o._measurement}: host=${o.host} ${o._field}=${o._value}`);
  },
  error(error) {
    console.error(error);
    console.log('Finished ERROR');
  },
  complete() {
    console.log('Finished SUCCESS');
  },
});
