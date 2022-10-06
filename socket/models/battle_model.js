const pool = require('../../utils/rmdb');
const { SocketException } = require('../../server/services/exceptions/socketException');

async function getFirstBattler(connection, battleID) {
  const firstSQL = `
  SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name, b.is_finish, 
  q.question_name, q.question_url, q.base_url, a.answer_number as answerNumber, a.test_case as testCase, a.output
  FROM battle as b, user as u, question as q, answer as a
  WHERE battle_id = ? AND u.user_id = b.first_user_id AND deleted = 0
  AND q.question_id = b.question_id AND q.question_id = a.question_id
  `;
  const [firstBattler] = await connection.execute(firstSQL, [battleID]);
  return firstBattler;
}

async function getSecondBattler(connection, battleID) {
  const secondSQL = `
  SELECT b.battle_name, b.first_user_id, b.second_user_id, u.user_name
  FROM battle as b, user as u 
  WHERE battle_id = ? AND u.user_id = b.second_user_id AND deleted = 0
  `;
  const [secondBattler] = await connection.execute(secondSQL, [battleID]);
  return secondBattler;
}

const queryBattler = async (battleID) => {
  const currentFunctionName = 'queryBattler';
  const connection = await pool.getConnection();

  // Get first battler for battle, if not, then send exception for not found / finished.
  const firstBattler = await getFirstBattler(connection, battleID);
  if (firstBattler.length === 0) {
    connection.release();
    throw new SocketException(
      'Battle not found',
      `User search battle(id=${battleID} not found)`,
      400,
      'battleNotFound',
      currentFunctionName,
    );
  }
  if (firstBattler[0].is_finish === 1) {
    connection.release();
    throw new SocketException(
      'Battle finished',
      `User search battle(id=${battleID} not found)`,
      400,
      'battleFinished',
      currentFunctionName,
    );
  }
  // Battle exists -> get details for frontend.
  const secondBattler = await getSecondBattler(connection, battleID);
  const responseObject = {
    battleID,
    battleName: firstBattler[0].battle_name,
    firstUserID: firstBattler[0].first_user_id,
    secondUserID: secondBattler[0].second_user_id,
    firstUserName: firstBattler[0].user_name,
    secondUserName: secondBattler[0].user_name,
    isFinish: firstBattler[0].is_finish,
    questionName: firstBattler[0].question_name,
    questionURL: process.env.AWS_DISTRIBUTION_NAME + firstBattler[0].question_url,
    baseURL: process.env.AWS_DISTRIBUTION_NAME + firstBattler[0].base_url,
  };
  connection.release();
  return responseObject;
};

const addBattleWatch = async (battleID) => {
  const updateSQL = `
  UPDATE battle SET watch_count = watch_count + 1 WHERE battle_id = ?;
  `;
  await pool.execute(updateSQL, [battleID]);
};

module.exports = { queryBattler, addBattleWatch };
