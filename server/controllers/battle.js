const Battle = require('../models/battle');

const createBattle = async (req, res) => {
  console.log('creating..');
  await Battle.createBattle();
  return res.status(200).send('battle created');
};

const getAllBattles = async (req, res) => {
  // TODO: for finish battle, where winner_url !== null
  const battles = await Battle.getAllBattles();
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
