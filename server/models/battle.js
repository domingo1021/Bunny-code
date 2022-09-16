const pool = require('../../utils/rmdb');

const createBattle = async () => {
  console.log('DB creating battle');
  return '';
};

const getAllBattles = async () => {
  const battleSQL = `
  SELECT battle_id as battleID, battle_name as battleName, watch_count as watchCount,
  first_user_id as firstUserID, second_user_id as secondUserID, winner_id as winnerID, is_finish as isFinish
  FROM battle
  WHERE deleted = 0
  ORDER BY watch_count DESC;
  `;
  const [battleResults] = await pool.execute(battleSQL);
  return battleResults;
};

module.exports = { createBattle, getAllBattles };
