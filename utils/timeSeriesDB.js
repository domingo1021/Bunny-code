require('dotenv').config();
const { InfluxDB } = require('@influxdata/influxdb-client');

const {
  INFLUX_URL, INFLUX_TOKEN,
} = process.env;

const influxClient = new InfluxDB({ url: INFLUX_URL, token: INFLUX_TOKEN });

module.exports = influxClient;
