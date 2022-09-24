require('dotenv').config();
const pool = require('../utils/rmdb');

const queryBattler = async (battleID) => {
  const connection = await pool.getConnection();
  const sqlFirst = `SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name, is_finish, q.question_name, q.question_url
  FROM battle as b, user as u, question as q
  WHERE battle_id = ? AND u.user_id = b.first_user_id AND q.question_id = b.question_id`;
  const sqlSecond = `SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name
  FROM battle as b, user as u 
  WHERE battle_id = ? AND u.user_id = b.second_user_id`;
  const [firstUser] = await connection.execute(sqlFirst, [battleID]);
  const [secondUser] = await connection.execute(sqlSecond, [battleID]);
  console.log(firstUser, secondUser);
  // TODO: Fix: fix if there is no battle;
  const responseObject = {
    battleID,
    battleName: firstUser[0].battle_name,
    firstUserID: firstUser[0].first_user_id,
    secondUserID: secondUser[0].second_user_id,
    firstUserName: firstUser[0].user_name,
    secondUserName: secondUser[0].user_name,
    isFinish: firstUser[0].is_finish,
    questionName: firstUser[0].question_name,
    questionURL: process.env.AWS_DISTRIBUTION_NAME + firstUser[0].question_url,
  };
  connection.release();
  return responseObject;
};

const createBattle = async (battleName, battleLevel, firstUserID, secondUserID) => {
  console.log(battleName, battleLevel, firstUserID, secondUserID);
  const connection = await pool.getConnection();
  const questionBattle = `
  SELECT q.question_id as questionID, a.answer_number as answerNumber, a.test_case as testCase, a.output 
  FROM question as q, answer as a 
  WHERE question_level = ? AND q.question_id = q.question_id;
  `;
  const [questionResult] = await connection.execute(questionBattle, [battleLevel]);
  console.log('questionResult: ', questionResult);
  const { questionID } = questionResult[0];
  const answer = questionResult.map((result) => {
    const answerObject = {};
    const testObject = {};
    testObject[`${result.testCase}`] = result.answer;
    answerObject[`answer-${result.answerNumber}`] = JSON.stringify(testObject);
    return answerObject;
  });
  console.log('Answer: ', answer);
  const battleSQL = `
  INSERT INTO battle (battle_name, first_user_id, second_user_id, question_id) 
  VALUES (?, ?, ?, ?)`;
  const [createResult] = await connection.execute(battleSQL, [battleName, firstUserID, secondUserID, questionID]);
  connection.release();
  return { battleID: createResult.insertId, answer };
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

const battleFinish = async (battleID, userID) => {
  const connection = await pool.getConnection();
  const finishSQL = 'UPDATE battle SET winner_id = ?, is_finish = 1 WHERE battle_id = ?';
  await connection.execute(finishSQL, [userID, battleID]);
  const userSQL = 'SELECT user_name FROM user WHERE user_id = ?';
  const [targetUser] = await connection.execute(userSQL, [userID]);
  connection.release();
  return {
    winnerID: userID,
    winnerName: targetUser[0].user_name,
  };
};

const addBattleWatch = async (battleID) => {
  const updateSQL = `
  UPDATE battle SET watch_count = watch_count + 1 WHERE battle_id = ?;
  `;
  await pool.execute(updateSQL, [battleID]);
};

const getWinnerData = async (battleID) => {
  const winnerSQL = `
    SELECT b.battle_name as battleName, b.watch_count as watchCount, b.winner_id as winnerID, b.winner_url as winnerURL, 
    b.question_id as questionID, q.question_name as questionName, q.question_url as questionURL, q.question_level as level
    u.user_name as userName, u.email, u.profile as profile, u.picture, u.level as userLevel
    FROM battle as b, question as q, user as u
    WHERE b.battle_id = ? AND b.winner_id = u.user_id AND b.question_id = q.question_id;
  `;
  const [winnerObject] = await pool.execute(winnerSQL, [battleID]);
  if (winnerObject.length === 0) {
    return {};
  }
  winnerObject[0].winnerURL = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].winnerURL;
  winnerObject[0].questionURL = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].questionURL;
  winnerObject[0].picture = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].picture;
  return winnerObject[0];
};

module.exports = {
  queryBattler,
  createBattle,
  getInvitations,
  acceptInvitation,
  battleFinish,
  addBattleWatch,
  getWinnerData,
};
