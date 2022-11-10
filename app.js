require('dotenv').config();
const express = require('express');
const cors = require('cors');
const Cache = require('./utils/cache');
const { APIException } = require('./server/services/exceptions/api_exception');
const { SQLException } = require('./server/services/exceptions/sql_exception');
const { Exception } = require('./server/services/exceptions/exception');

const app = express();
const {
  TEST_PORT, SERVER_PORT, NODE_ENV, API_VERSION, WHITE_LIST,
} = process.env;
const port = NODE_ENV === 'test' ? TEST_PORT : SERVER_PORT;
const corsOptions = {
  origin: WHITE_LIST,
  optionsSuccessStatus: 200,
};

app.use(cors(corsOptions));
app.use(express.static('public'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API routes
app.use(`/api/${API_VERSION}`, [
  require('./server/routes/user'),
  require('./server/routes/compiler'),
  require('./server/routes/project'),
  require('./server/routes/battle'),
]);

// Page not found
app.use((req, res, next) => {
  res.status(404).sendFile(`${__dirname}/public/404.html`);
});

// Error handling
app.use((err, req, res, next) => {
  console.log(err.fullLog);
  if (err instanceof SQLException) {
    return res.status(400).json({ msg: err.message });
  }
  if (err instanceof APIException) {
    return res.status(err.status).json({ msg: err.message });
  }
  if (err instanceof Exception) {
    return res.status(500).json({ msg: err.message });
  }
  console.log(err);
  return res.status(500).json({ msg: 'Server too busy, please try again' });
});

const httpServer = app.listen(port, () => {
  Cache.connect().catch(() => {
    console.log('redis connect fail');
  });
  console.log(`Listening at port ${SERVER_PORT}`);
});

module.exports = { app, httpServer };
