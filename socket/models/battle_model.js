const pool = require('../../utils/rmdb');
const { SocketException } = require('../../server/services/exceptions/socketException');
const { Exception } = require('../../server/services/exceptions/exception');

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

async function getQuestionAnswer(connection, battleLevel) {
  const questionBattle = `
  SELECT q.question_id as questionID, a.answer_number as answerNumber, a.test_case as testCase, a.output 
  FROM question as q, answer as a 
  WHERE q.question_level = ? AND a.question_id = q.question_id;
  `;
  const [questionResult] = await connection.execute(questionBattle, [battleLevel]);
  const { questionID } = questionResult[0];
  const answer = questionResult.map((result) => {
    const answerObject = {};
    const testObject = {};
    testObject[`${result.testCase}`] = result.output;
    answerObject[`answer-${result.answerNumber}`] = JSON.stringify(testObject);
    return answerObject;
  });
  return { questionID, answer };
}

async function insertBattle(connection, battleName, firstUserID, secondUserID, questionID) {
  const battleSQL = `
  INSERT INTO battle (battle_name, first_user_id, second_user_id, question_id) 
  VALUES (?, ?, ?, ?)`;
  try {
    const [newBattle] = await connection.execute(
      battleSQL,
      [battleName, firstUserID, secondUserID, questionID],
    );
    return newBattle.insertId;
  } catch (error) {
    console.log(error);
    if (error.sqlMessage.includes('Duplicate')) {
      throw new SocketException(
        'Battle name already in use',
        `User search battle(name=${battleName}) failed)`,
        400,
        'battleFailed',
        'insertBattle',
      );
    }
    throw new SocketException(
      'Create battle failed',
      `Unexpected failed when creating battle ${error.stack}`,
      400,
      'battleFailed',
      'insertBattle',
    );
  }
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

const createBattle = async (battleName, battleLevel, firstUserID, secondUserID) => {
  const connection = await pool.getConnection();
  const { questionID, answer } = await getQuestionAnswer(connection, battleLevel);
  const battleID = await insertBattle(connection, battleName, firstUserID, secondUserID, questionID);
  return { battleID, answer };
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

const getWinnerData = async (battleID) => {
  const winnerSQL = `
    SELECT b.battle_name as battleName, b.watch_count as watchCount, b.winner_id as winnerID, b.winner_url as winnerURL, 
    b.question_id as questionID, q.question_name as questionName, q.question_url as questionURL, q.question_level as level,
    u.user_name as userName, u.email, u.profile as profile, u.picture, u.level as userLevel
    FROM battle as b, question as q, user as u
    WHERE b.battle_id = ? AND b.winner_id = u.user_id AND b.question_id = q.question_id;
  `;
  const [winnerObject] = await pool.execute(winnerSQL, [battleID]);
  if (winnerObject.length === 0) {
    return null;
  }
  winnerObject[0].winnerURL = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].winnerURL;
  winnerObject[0].questionURL = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].questionURL;
  winnerObject[0].picture = process.env.AWS_DISTRIBUTION_NAME + winnerObject[0].picture;
  return winnerObject[0];
};

const deleteBattle = async (battleID) => {
  const deleteSQL = `
    UPDATE battle SET deleted = 1, is_finish = 1 WHERE battle_id = ?;
  `;
  try {
    await pool.execute(deleteSQL, [battleID]);
  } catch (error) {
    throw new Exception('Internal server error', `Delete battle(id=${battleID}) failed.`, 'deleteBattle');
  }
};

module.exports = {
  queryBattler, addBattleWatch, createBattle, battleFinish, getWinnerData, deleteBattle,
};
