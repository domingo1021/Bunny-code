require('dotenv').config();
const Battle = require('../models/battle');

const createBattle = async (req, res) => {
  console.log('creating..');
  await Battle.createBattle();
  return res.status(200).send('battle created');
};

const getAllBattles = async (req, res) => {
  const [firstBattler, secondBattler] = await Battle.getAllBattles();
  const battles = [];
  for (let i = 0; i < firstBattler.length; i += 1) {
    firstBattler[i].firstUserPicture = process.env.AWS_DISTRIBUTION_NAME + firstBattler[i].firstUserPicture;
    secondBattler[i].secondUserPicture = process.env.AWS_DISTRIBUTION_NAME + secondBattler[i].secondUserPicture;
    battles.push({ ...firstBattler[i], ...secondBattler[i] });
  }
  const finishBattle = [];
  const continueBattle = [];
  battles.forEach((battle) => {
    if (battle.isFinish) {
      finishBattle.push(battle);
    } else {
      continueBattle.push(battle);
    }
  });
  return res.status(200).json({
    data: {
      finish: finishBattle,
      still: continueBattle,
    },
  });
};

module.exports = { createBattle, getAllBattles };
