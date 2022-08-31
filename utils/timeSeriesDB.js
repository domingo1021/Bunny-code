require('dotenv').config();
const { InfluxDB } = require('@influxdata/influxdb-client');

// You can generate an API token from the "API Tokens Tab" in the UI
const {
  INFLUX_URL, INFLUX_TOKEN, INFLUX_ORG, INFLUX_BUCKET,
} = process.env;

console.log(INFLUX_URL, INFLUX_TOKEN, INFLUX_ORG, INFLUX_BUCKET);

const client = new InfluxDB({ url: INFLUX_URL, token: INFLUX_TOKEN });

// Write data
const { Point } = require('@influxdata/influxdb-client');

const writeApi = client.getWriteApi(INFLUX_ORG, INFLUX_BUCKET);
writeApi.useDefaultTags({ host: 'host1' });

const point = new Point('mem').floatField('used_percent', 23.43234543);
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

const query = 'from(bucket: "bunny") |> range(start: -1h)';
queryApi.queryRows(query, {
  next(row, tableMeta) {
    const o = tableMeta.toObject(row);
    console.log(`${o._time} ${o._measurement}: ${o._field}=${o._value}`);
  },
  error(error) {
    console.error(error);
    console.log('Finished ERROR');
  },
  complete() {
    console.log('Finished SUCCESS');
  },
});
