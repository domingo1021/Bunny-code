const Battle = require('../models/battle');

const createBattle = async (req, res) => {
  console.log('creating..');
  await Battle.createBattle();
  return res.status(200).send('battle created');
};

module.exports = { createBattle };
