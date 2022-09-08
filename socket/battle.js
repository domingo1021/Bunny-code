const pool = require('../utils/rmdb');

const queryBattler = async (battleID) => {
  const sql = 'SELECT first_user_id, second_user_id FROM battle WHERE battle_id = ?';
  const [battleResponse] = await pool.execute(sql, battleID);
  return [battleResponse[0].first_user_id, battleResponse[0].second_user_id];
};

module.exports = { queryBattler };
