require('dotenv').config();
const pool = require('../../utils/rmdb');
const Cache = require('../../utils/cache');
const { checkCacheReady } = require('../services/service');
const Battle = require('../models/battle_model');
const { CLIENT_CATEGORY } = require('../util');
const { SocketException } = require('../../server/services/exceptions/socketException');

let ioServer;

async function queryBattler(queryObject) {
  const socket = this;
  checkCacheReady();
  console.log(`user in, with queryObject: ${JSON.stringify(queryObject)}`);

  socket.category = 'battle';
  socket.battleID = `battle-${queryObject.battleID}`;

  // get battle info with query object
  const battle = await Battle.queryBattler(queryObject.battleID);

  // battle still going on, then let user join battle socket room.
  socket.join(socket.battleID);
  const battleCache = await Cache.HGETALL(`${socket.battleID}`);

  // response battle not found if cannot find battle from cache.
  if (Object.keys(battleCache).length === 0) {
    throw new SocketException(
      'Battle not found',
      `Get cache battle(id=${queryObject.battleID} not found)`,
      400,
      'battleNotFound',
      'queryBattler',
    );
  }

  // check user authorization.
  let userCategory = CLIENT_CATEGORY.visitor;
  if ([battle.firstUserID, battle.secondUserID].includes(socket.user.id)) {
    userCategory = CLIENT_CATEGORY.self;
  } else {
    await Battle.addBattleWatch(queryObject.battleID);
  }

  socket.emit('returnBattler', {
    battleResponse: battle,
    userID: socket.user.id,
    category: userCategory,
    firstUserObject: {
      ready: JSON.parse(battleCache[`${battle.firstUserID}`]).ready,
      codes: JSON.parse(battleCache[`${battle.firstUserID}`]).codes,
      chance: JSON.parse(battleCache[`${battle.firstUserID}`]).chance,
    },
    secondUserObject: {
      ready: JSON.parse(battleCache[`${battle.secondUserID}`]).ready,
      codes: JSON.parse(battleCache[`${battle.secondUserID}`]).codes,
      chance: JSON.parse(battleCache[`${battle.secondUserID}`]).chance,
    },
  });
}

const createBattle = async (battleName, battleLevel, firstUserID, secondUserID) => {
  console.log(battleName, battleLevel, firstUserID, secondUserID);
  const connection = await pool.getConnection();
  const questionBattle = `
  SELECT q.question_id as questionID, a.answer_number as answerNumber, a.test_case as testCase, a.output 
  FROM question as q, answer as a 
  WHERE q.question_level = ? AND a.question_id = q.question_id;
  `;
  const [questionResult] = await connection.execute(questionBattle, [battleLevel]);
  console.log('questionResult: ', questionResult);
  const { questionID } = questionResult[0];
  const answer = questionResult.map((result) => {
    const answerObject = {};
    const testObject = {};
    testObject[`${result.testCase}`] = result.output;
    answerObject[`answer-${result.answerNumber}`] = JSON.stringify(testObject);
    return answerObject;
  });
  console.log('Answer: ', answer);
  const battleSQL = `
  INSERT INTO battle (battle_name, first_user_id, second_user_id, question_id) 
  VALUES (?, ?, ?, ?)`;
  let battleID;
  try {
    const [createResult] = await connection.execute(battleSQL, [battleName, firstUserID, secondUserID, questionID]);
    battleID = createResult.insertId;
  } catch (error) {
    return { created: false };
  }
  connection.release();
  return { battleID, answer, created: true };
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
  await pool.execute(deleteSQL, [battleID]);
};

module.exports = (io) => {
  ioServer = io;

  return {
    queryBattler,
    createBattle,
    getInvitations,
    acceptInvitation,
    battleFinish,
    getWinnerData,
    deleteBattle,
  };
};
