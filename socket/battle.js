const pool = require('../utils/rmdb');

const queryBattler = async (battleID) => {
  const connection = await pool.getConnection();
  const sqlFirst = `SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name
  FROM battle as b 
  LEFT JOIN user as u ON u.user_id = b.first_user_id 
  WHERE battle_id = ?`;
  const sqlSecond = `SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name
  FROM battle as b 
  LEFT JOIN user as u ON u.user_id = b.second_user_id 
  WHERE battle_id = ?
  `;
  const [firstUser] = await connection.execute(sqlFirst, battleID);
  const [secondUser] = await connection.execute(sqlSecond, battleID);
  // TODO: Fix: fix if there is no battle;
  const responseObject = {
    battleID,
    battleName: firstUser[0].battle_name,
    firstUserID: firstUser[0].first_user_id,
    secondUserID: secondUser[0].second_user_id,
    firstUserName: firstUser[0].user_name,
    secondUserName: secondUser[0].user_name,
  };
  connection.release();
  return responseObject;
};

module.exports = { queryBattler };
