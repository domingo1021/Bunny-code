require('dotenv').config();
const express = require('express');

const app = express();

app.use(express.static('public'));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// API routes
// app.use('/api/' + API_VERSION);

// Page not found
app.use((req, res, next) => {
  res.status(404).sendFile(`${__dirname}/public/404.html`);
});

// Error handling
app.use((err, req, res, next) => {
  res.status(500).send('Internal Server Error');
});

app.listen(process.env.SERVER_PORT, () => {
  console.log(`Listening at port ${process.env.SERVER_PORT}`);
});
