require('dotenv').config();
const express = require('express');

const { SERVER_PORT, API_VERSION } = process.env;

const app = express();

app.use(express.static('public'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API routes
app.use(`/api/${API_VERSION}`, require('./server/routes/compiler'));

// Page not found
app.use((req, res, next) => {
  res.status(404).sendFile(`${__dirname}/public/404.html`);
});

// Error handling
app.use((err, req, res, next) => {
  console.log(err);
  res.status(500).send('Internal Server Error');
});

app.listen(SERVER_PORT, () => {
  console.log(`Listening at port ${SERVER_PORT}`);
});
