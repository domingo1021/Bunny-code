require('dotenv');
const { InfluxDB } = require('@influxdata/influxdb-client');

// You can generate an API token from the "API Tokens Tab" in the UI
const token = process.env.INFLUXDB_TOKEN;
const org = 'BunnyCode';
const bucket = 'bunny';

const client = new InfluxDB({ url: 'http://54.248.6.0:8086', token });

const { Point } = require('@influxdata/influxdb-client');

const writeApi = client.getWriteApi(org, bucket);
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

const queryApi = client.getQueryApi(org);

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
