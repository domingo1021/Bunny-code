require('dotenv').config();
const express = require('express');
const cors = require('cors');
const Cache = require('./utils/cache');
const { ServiceException } = require('./server/services/service');
const { APIException } = require('./server/services/exceptions/api_exception');
const { SQLException } = require('./server/services/exceptions/sql_exception');

const { SERVER_PORT, API_VERSION } = process.env;

const app = express();

app.use(cors());
app.use(express.static('public'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API routes
app.use(`/api/${API_VERSION}`, [
  require('./server/routes/user'),
  require('./server/routes/compiler'),
  require('./server/routes/project'),
  require('./server/routes/marketing'),
  require('./server/routes/battle'),
]);

// Page not found
app.use((req, res, next) => {
  res.status(404).sendFile(`${__dirname}/public/404.html`);
});

// Error handling
// TODO: handle more exception.
app.use((err, req, res, next) => {
  console.log(err.fullLog);
  if (err instanceof ServiceException) {
    return res.send({ msg: err.message });
  }
  if (err instanceof SQLException) {
    return res.status(400).json({ msg: err.message });
  }
  if (err instanceof APIException) {
    return res.status(err.status).json({ msg: err.message });
  }
  console.log(err);
  return res.status(500).send('Internal Server Error');
});

const httpServer = app.listen(SERVER_PORT, () => {
  Cache.connect().catch(() => {
    console.log('redis connect fail');
  });
  console.log(`Listening at port ${SERVER_PORT}`);
});

module.exports = httpServer;
