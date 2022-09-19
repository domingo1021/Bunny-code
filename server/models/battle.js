const pool = require('../../utils/rmdb');

const createBattle = async () => {
  console.log('DB creating battle');
  return '';
};

const getAllBattles = async () => {
  const userOneSQL = `
  SELECT battle_id as battleID, battle_name as battleName, watch_count as watchCount,
  first_user_id as firstUserID, is_finish as isFinish, 
  u.user_name as firstUserName, u.level as firstUserLevel, u.picture as firstUserPicture
  FROM battle as b, user as u
  WHERE deleted = 0 AND u.user_id = b.first_user_id
  ORDER BY watch_count DESC;
  `;
  const userTwoSQL = `
  SELECT battle_id as battleID, battle_name as battleName, watch_count as watchCount,
  second_user_id as secondUserID, winner_id as winnerID, winner_url as winnerURL, is_finish as isFinish, 
  u.user_name as secondUserName, u.level as secondUserLevel, u.picture as secondUserPicture
  FROM battle as b, user as u
  WHERE deleted = 0 AND u.user_id = b.second_user_id
  ORDER BY watch_count DESC;
  `;
  const connection = await pool.getConnection();
  const [battleOne] = await connection.execute(userOneSQL);
  const [battleTwo] = await connection.execute(userTwoSQL);
  connection.release();
  return [battleOne, battleTwo];
};

const writeBattleFile = async (battleID, winnerURL) => {
  const writeFileSQL = `
    UPDATE battle SET winner_url = ? WHERE battle_id = ?;
  `;
  await pool.execute(writeFileSQL, [winnerURL, battleID]);
};

module.exports = {
  createBattle, getAllBattles, writeBattleFile,
};
