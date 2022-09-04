require('dotenv').config();
const express = require('express');
const cors = require('cors');

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
]);

// Page not found
app.use((req, res, next) => {
  res.status(404).sendFile(`${__dirname}/public/404.html`);
});

// Error handling
app.use((err, req, res, next) => {
  console.log(err);
  res.status(500).send('Internal Server Error');
});

const httpServer = app.listen(SERVER_PORT, () => {
  console.log(`Listening at port ${SERVER_PORT}`);
});

module.exports = httpServer;
