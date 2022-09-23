require('dotenv').config();
const Battle = require('../models/battle');

const createBattle = async (req, res) => {
  console.log('creating..');
  await Battle.createBattle();
  return res.status(200).send('battle created');
};

const getBattles = async (req, res) => {
  const { status, type } = req.query;
  let { keyword, paging } = req.query;
  if (!keyword) {
    keyword = '';
  }
  let isFinish = 0;
  if (status === 'finished') {
    isFinish = 1;
  }
  if (!+paging) {
    paging = 0;
  }
  let firstBattler;
  let secondBattler;
  switch (type) {
    case 'user': {
      [firstBattler, secondBattler] = await Battle.getBattlesByUser(keyword, isFinish, paging);
      break;
    }
    case 'question': {
      [firstBattler, secondBattler] = await Battle.getBattlesByQuestion(keyword, isFinish, paging);
      break;
    }
    default: {
      [firstBattler, secondBattler] = await Battle.getBattles(keyword, isFinish, paging);
      break;
    }
  }
  if (!firstBattler || !secondBattler) {
    return res.status(200).json({
      data: [],
    });
  }
  const battles = [];
  for (let i = 0; i < firstBattler.length; i += 1) {
    firstBattler[i].firstUserPicture = process.env.AWS_DISTRIBUTION_NAME + firstBattler[i].firstUserPicture;
    secondBattler[i].secondUserPicture = process.env.AWS_DISTRIBUTION_NAME + secondBattler[i].secondUserPicture;
    battles.push({ ...firstBattler[i], ...secondBattler[i] });
  }
  return res.status(200).json({
    data: battles,
  });
};

const ifBattleExists = async (req, res) => {
  const { battleName } = req.params;
  if (battleName.length > 30) {
    return res.status(400).json({
      msg: 'Battle name should not be larger than 30 characters.',
    });
  }
  // TODO: check battle detail.
  const checkExists = await Battle.ifBattleExists(battleName);
  if (checkExists === 1) {
    return res.status(400).json({
      msg: 'Battle name already exists.',
    });
  }
  return res.status(200).json({
    data: false,
  });
};

module.exports = {
  createBattle, getBattles, ifBattleExists,
};
