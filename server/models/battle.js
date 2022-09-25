const pool = require('../../utils/rmdb');

const createBattle = async () => {
  console.log('DB creating battle');
  return '';
};

const getBattles = async (keyword, status, paging) => {
  const userOneSQL = `
  SELECT b.battle_id as battleID, b.battle_name as battleName, b.watch_count as watchCount,
  b.first_user_id as firstUserID, b.is_finish as isFinish, b.question_id as questionID,
  q.question_name as questionName, q.question_level as questionLevel,
  u.user_name as firstUserName, u.level as firstUserLevel, u.picture as firstUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (b.battle_name LIKE ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const userTwoSQL = `
  SELECT b.battle_id as battleID,
  b.second_user_id as secondUserID, b.winner_id as winnerID, b.winner_url as winnerURL, b.is_finish as isFinish, 
  u.user_name as secondUserName, u.level as secondUserLevel, u.picture as secondUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (b.battle_name LIKE ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const likeString = `%${keyword}%`;
  const limitCount = paging * 6;
  const connection = await pool.getConnection();
  const [battleOne] = await connection.query(userOneSQL, [status, likeString, 6, limitCount]);
  const [battleTwo] = await connection.query(userTwoSQL, [status, likeString, 6, limitCount]);
  connection.release();
  return [battleOne, battleTwo];
};

const getBattlesByQuestion = async (keyword, status, paging) => {
  const userOneSQL = `
  SELECT b.battle_id as battleID, b.battle_name as battleName, b.watch_count as watchCount,
  b.first_user_id as firstUserID, b.is_finish as isFinish, b.question_id as questionID,
  q.question_name as questionName, q.question_level as questionLevel,
  u.user_name as firstUserName, u.level as firstUserLevel, u.picture as firstUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (q.question_name LIKE ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const userTwoSQL = `
  SELECT b.battle_id as battleID,
  b.second_user_id as secondUserID, b.winner_id as winnerID, b.winner_url as winnerURL, b.is_finish as isFinish, 
  u.user_name as secondUserName, u.level as secondUserLevel, u.picture as secondUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (q.question_name LIKE ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const likeString = `%${keyword}%`;
  const limitCount = paging * 6;
  const connection = await pool.getConnection();
  const [battleOne] = await connection.query(userOneSQL, [status, likeString, 6, limitCount]);
  const [battleTwo] = await connection.query(userTwoSQL, [status, likeString, 6, limitCount]);
  connection.release();
  return [battleOne, battleTwo];
};

const getBattlesByUser = async (keyword, status, paging) => {
  const connection = await pool.getConnection();
  const likeString = `%${keyword}%`;
  const searchUserSQL = `
    SELECT user_id as userID FROM user WHERE user_name LIKE ?;
  `;
  const userOneSQL = `
  SELECT b.battle_id as battleID, b.battle_name as battleName, b.watch_count as watchCount,
  b.first_user_id as firstUserID, b.is_finish as isFinish, b.question_id as questionID,
  q.question_name as questionName, q.question_level as questionLevel,
  u.user_name as firstUserName, u.level as firstUserLevel, u.picture as firstUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (first_user_id = ? OR b.second_user_id = ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const userTwoSQL = `
  SELECT b.battle_id as battleID,
  b.second_user_id as secondUserID, b.winner_id as winnerID, b.winner_url as winnerURL, b.is_finish as isFinish, 
  u.user_name as secondUserName, u.level as secondUserLevel, u.picture as secondUserPicture
  FROM battle as b, user as u, question as q
  WHERE b.is_finish = ? AND deleted = 0 AND u.user_id = b.first_user_id 
  AND b.question_id = q.question_id AND (first_user_id = ? OR b.second_user_id = ?)
  ORDER BY watch_count DESC
  LIMIT ? OFFSET ?;
  `;
  const limitCount = paging * 6;
  const [targetUser] = await connection.execute(searchUserSQL, [likeString]);
  if (targetUser.length === 0) {
    return [];
  }
  const [battleOne] = await connection.query(userOneSQL, [status, targetUser[0].userID, targetUser[0].userID, 6, limitCount]);
  const [battleTwo] = await connection.query(userTwoSQL, [status, targetUser[0].userID, targetUser[0].userID, 6, limitCount]);
  connection.release();
  return [battleOne, battleTwo];
};

const writeBattleFile = async (battleID, winnerURL) => {
  const writeFileSQL = `
    UPDATE battle SET winner_url = ? WHERE battle_id = ?;
  `;
  await pool.execute(writeFileSQL, [winnerURL, battleID]);
};

const ifBattleExists = async (battleName) => {
  const checkSQL = `
  SELECT count(battle_id) as count FROM battle WHERE battle_name = ? 
  `;
  const [checkResponse] = await pool.execute(checkSQL, [battleName]);
  return checkResponse[0].count;
};

module.exports = {
  createBattle,
  getBattles,
  writeBattleFile,
  ifBattleExists,
  getBattlesByUser,
  getBattlesByQuestion,
};
