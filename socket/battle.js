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

const createBattle = async (battleName, battleLevel, firstUserID, secondUserID) => {
  const connection = await pool.getConnection();
  const questionBattle = `
  SELECT question_id as questionID FROM question WHERE question_level = ?;
  `;
  const [questionResult] = await connection.execute(questionBattle, battleLevel);
  const { questionID } = questionResult[0];
  const battleSQL = `
  INSERT INTO battle (battle_name, first_user_id, second_user_id, question_id) 
  VALUES (?, ?, ?, ?)`;
  const [createResult] = await connection.execute(battleSQL, [battleName, firstUserID, secondUserID, questionID]);
  connection.release();
  return { battleID: createResult.insertId };
};

const getInvitations = async (userID) => {
  const invitationSQL = `
  SELECT b.battle_id as battleID, b.battle_name as battleName, b.first_user_id as first_user_id, b.create_at as createAt, u.user_name as userName 
  FROM battle as b, user as u 
  WHERE compete_at < ? AND second_user_id = ? AND is_consensus = 0 AND b.first_user_id = u.user_id;
  `;
  const date = new Date();
  date.setDate(date.getDate() + 7);
  const [invitationResponse] = await pool.execute(invitationSQL, [date, userID]);
  console.log('invitation response: ', invitationResponse);
  return invitationResponse;
};

const acceptInvitation = async (battleID, userID) => {
  const acceptSQL = 'UPDATE battle SET is_consensus = 1 WHERE battle_id = ? AND second_user_id = ?';
  const [updateResponse] = await pool.execute(acceptSQL, [battleID, userID]);
  return updateResponse;
};

module.exports = {
  queryBattler, createBattle, getInvitations, acceptInvitation,
};
