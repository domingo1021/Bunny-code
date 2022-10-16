require('dotenv').config();
const Cache = require('../../utils/cache');
const Battle = require('../models/battle_model');
const { CLIENT_CATEGORY } = require('../services/service');
const { SocketException } = require('../../server/services/exceptions/socketException');
const { checkCacheReady } = require('../services/service');
const { compile } = require('../../server/services/service');

let ioServer;

async function inviteBattle(socket, emitObject) {
  checkCacheReady();
  const battleObject = {
    socketID: socket.id,
    name: emitObject.battleName,
    level: emitObject.battleLevel,
    firstUserID: socket.user.id,
    firstUserName: socket.user.name,
  };

  // check if user invite battle too frequently.
  const redisGet = await Cache.hGetAll(`${socket.id}`);
  if (Object.keys(redisGet)[0]) {
    console.log(`User(id=${socket.user.id}) battle invitation is blocked, too many request !`);
    return socket.emit('inviteFailed', 'Pleas wait for 10 seconds for another invitation.');
  }

  // set user inviate battle into cache.
  await Cache.HSETNX(`${socket.id}`, `${socket.user.id}`, JSON.stringify(battleObject));
  console.log(`User(id=${socket.user.id}) create a battle with key(socket_id=${socket.id})`);

  // delete tmp hash key after 10 seconds.
  setTimeout(async () => {
    console.log(`ready to delete tmp battle with socket id key (socket_id=${socket.id})`);
    await Cache.HDEL(`${socket.id}`, `${socket.user.id}`);
  }, 10000);

  // io broadcast battle invitation to socket client
  return ioServer.emit('userInvite', battleObject);
}

async function isolatedAcceptBattle(socketID, firstUserID) {
  checkCacheReady();
  try {
    return await Cache.executeIsolated(async (isolatedClient) => {
      await isolatedClient.watch(`${socketID}`);
      const battle = await isolatedClient.HGETALL(`${socketID}`);
      await isolatedClient.HDEL(`${socketID}`, `${firstUserID}`);
      return battle;
    });
  } catch (error) {
    throw new SocketException(
      'Battle accept timout, failed to create battle',
      `User search tmp battle(socket_id=${socketID} not found)`,
      400,
      'battleFailed',
      'isolatedAcceptBattle',
    );
  }
}

async function acceptBattle(socket, emitObject) {
  const { socketID, firstUserID } = emitObject;
  checkCacheReady();

  // isolated get battle object, and deal with race condition.
  const battleObject = await isolatedAcceptBattle(socketID, firstUserID);

  // battle invitation timeout, battle already been remove from cache
  console.log('battleObject: ', battleObject);
  if (Object.keys(battleObject)[0] !== `${firstUserID}`) {
    return socket.emit('battleFailed', 'Battle accept timout, failed to create battle');
  }

  // block invitor to accept battle.
  if (firstUserID === socket.user.id) {
    return socket.emit('battleFailed', 'Battler user should not be the same.');
  }

  // Create a battle in MySQL.
  const battlePayload = JSON.parse(battleObject[`${firstUserID}`]);
  const { battleID, answer } = await Battle.createBattle(
    battlePayload.name,
    battlePayload.level,
    battlePayload.firstUserID,
    socket.user.id,
  );

  socket.battleID = `battle-${battleID}`;

  const cacheObject = {};
  cacheObject[`${battlePayload.firstUserID}`] = JSON.stringify({ ready: 0, codes: '', chance: 3 });
  cacheObject[`${socket.user.id}`] = JSON.stringify({ ready: 0, codes: '', chance: 3 });
  console.log(answer);
  answer.forEach((answerObject) => {
    cacheObject[`${Object.keys(answerObject)[0]}`] = Object.values(answerObject)[0];
  });
  const cacheBattleResult = await Cache.HSET(`${socket.battleID}`, cacheObject);
  if (cacheBattleResult) {
    ioServer.to(socketID).emit('battleCreated', {
      battleID,
    });
    socket.emit('battleCreated', {
      battleID,
    });
  }
}

async function queryBattler(socket, queryObject) {
  checkCacheReady();

  socket.category = 'battle';
  socket.battleID = `battle-${queryObject.battleID}`;
  console.log(`User(id=${socket.user.id}) with socket(id=${socket.id}) come into battle(${socket.battleID})`);

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

async function setReady(socket, emitObject) {
  checkCacheReady();

  // check if socket user is himself.
  if (emitObject.currentUserID !== socket.user.id) {
    return;
  }

  // Get ready state from redis hash data.
  const battlerInfo = await Cache.HGET(`${socket.battleID}`, `${emitObject.currentUserID}`);
  if (battlerInfo === null) {
    return;
  }
  const battlerObject = JSON.parse(battlerInfo);

  // set ready set=1 to redis hash data.
  battlerObject.ready = '1';
  await Cache.HSET(`${socket.battleID}`, `${emitObject.currentUserID}`, JSON.stringify(battlerObject));

  // emit to socket user and room, say user ready.
  socket.emit('userReady', {
    readyUserID: socket.user.id,
  });
  socket.to(`${socket.battleID}`).emit('userReady', {
    readyUserID: socket.user.id,
  });

  // if all battler ready, then start the game !
  const allBattler = await Cache.hGetAll(`${socket.battleID}`);
  if (JSON.parse(allBattler[`${emitObject.currentUserID}`]).ready === '1' && JSON.parse(allBattler[`${emitObject.anotherUserID}`]).ready === '1') {
    socket.to(`${socket.battleID}`).emit('battleStart');
    socket.emit('battleStart');
  }
}

async function broadcastNewCodes(socket, recordObject) {
  await Cache.executeIsolated(async (isolatedClient) => {
    await isolatedClient.watch(socket.battleID);
    const battlerInfo = await isolatedClient.HGET(`${socket.battleID}`, `${socket.user.id}`);
    const battlerObject = JSON.parse(battlerInfo);
    battlerObject.codes = recordObject.newCodes;
    await isolatedClient.HSET(`${socket.battleID}`, `${socket.user.id}`, JSON.stringify(battlerObject));
  });
  socket.to(socket.battleID).emit('newCodes', recordObject);
}

function prepareTestCase(corrections, jsonResult, answers) {
  const testCase = [];
  for (let i = 0; i < answers.length; i += 1) {
    if (corrections[i]) {
      testCase.push({ ...answers[i], 'Compile result': jsonResult[i] });
    } else {
      if (i > 2) {
        testCase.push({
          'Hided test case': 'answer',
          'Compile result': 'Unexpected result',
        });
      } else {
        testCase.push({ ...answers[i], 'Compile result': jsonResult[i] });
      }
      break;
    }
  }
  return testCase;
}

async function compile(socket, queryObject) {
  const battleObject = await Cache.hGetAll(`${socket.battleID}`);
  const currentUserObject = JSON.parse(battleObject[`${socket.user.id}`]);

  // block compile if user have no chance;
  if (currentUserObject.chance === 0) {
    console.log(`User(id=${socket.user.id}) is trying to compile code after have no chance.`);
    return;
  }

  // set user new chance (minus 1);
  currentUserObject.chance -= 1;
  await Cache.HSET(`${socket.battleID}`, `${socket.user.id}`, JSON.stringify(currentUserObject));

  // run codeing sanbox (like leetcode), for specific question (5 test case), and get answer.
  let compilerResult;
  let resultStatus
  try {
    compilerResult = await compile('battleValley', queryObject.codes, {
      battleID: queryObject.battleID,
      battlerNumer: queryObject.battlerNumber,
      questionName: queryObject.questionName,
    });
    resultStatus = true
  } catch (error) {
    // compile error, make error message to compile result, will send back with test case.
    console.log(error.fullLog);
    compilerResult = error.message;
  }

  // get question answer from cache (better performance in checking answer).
  const answers = [];
  const answerIndex = [1, 2, 3, 4, 5];
  answerIndex.forEach((index) => {
    answers.push(JSON.parse(battleObject[`answer-${index}`]));
  });

  let corrections = [];
  const jsonResult = [];

  // check the answer;
  try {
    if (resultStatus === 'success') {
      answers.forEach((answer, index) => {
        let currAnswer = Object.values(answer)[0];
        if (currAnswer.includes('[')) {
          currAnswer = JSON.stringify(JSON.parse(currAnswer));
        }
        let result = JSON.parse(compilerResult.replaceAll('\n', '').replaceAll("'", '"').replaceAll('undefined', 'null'))[index];
        if (typeof result === 'object') {
          result = JSON.stringify(result);
        } else if (typeof result === 'number') {
          result = `${result}`;
        }
        jsonResult.push(result);
        corrections.push(currAnswer === result);
      });
    } else {
      corrections = [false];
      jsonResult.push(compilerResult);
    }
  } catch (error) {
    console.log(`Error occur in checking answer for compile result(${compilerResult})\n 
    Error stack: ${error.stack}`);
    corrections.push(false);
  }
  console.log(`User(id=${socket.user.id}) compile with correction: ${corrections}`);

  // Send user the test case which is wrong.
  const testCase = prepareTestCase(corrections, jsonResult, answers);
  socket.to(socket.battleID).emit(
    'compileDone',
    {
      battlerNumber: queryObject.battlerNumber,
      compilerResult,
      testCase,
      compileChance: currentUserObject.chance,
    },
  );
  socket.emit('compileDone', {
    battlerNumber: queryObject.battlerNumber,
    compilerResult,
    testCase,
    compileChance: currentUserObject.chance,
  });

  // Check is the battler compiler all true, then tag as winner.
  const isWinner = corrections.every((correction) => correction);
  console.log('Winner status: ', isWinner);
  if (isWinner) {
    console.log(`The winner is ${socket.user.id}`);
    await Battle.battleFinish(queryObject.battleID, socket.user.id);
    await Cache.del(`${socket.battleID}`);
    socket.to(socket.battleID).emit('battleOver', {
      winnerID: socket.user.id,
      winnerName: socket.user.name,
      reason: `${socket.user.name} just compiled with the right answer`,
    });
    socket.emit('battleOver', {
      winnerID: socket.user.id,
      winnerName: socket.user.name,
      reason: 'For just compiled with the right answer',
    });
  } else if (!isWinner && currentUserObject.chance === 0) {
    console.log(`${socket.battleID} is terminated becaurse Ueer(id=${socket.user.id} has no chance.)`);
    await Battle.deleteBattle(queryObject.battleID);
    await Cache.del(`${socket.battleID}`);
    socket.to(socket.battleID).emit('battleTerminate', {
      reason: `${socket.user.name} just ran out of compile chance`,
    });
    socket.emit('battleTerminate', {
      reason: `${socket.user.name} just ran out of compile chance`,
    });
  }
}

async function getWinnerData(socket, queryObject) {
  if (!queryObject.battleID) {
    return;
  }
  const winnerData = await Battle.getWinnerData(queryObject.battleID);
  if (winnerData === null) {
    socket.emit('battleNotFound');
  }
  socket.emit('winnerData', winnerData);
}

async function leaveBattle(socket) {
  console.log(`User(id=${socket.user.id}) with socket(id=${socket.id}) leave battle(${socket.battleID})`);
  if (!socket.battleID) {
    return;
  }
  checkCacheReady();
  await Cache.executeIsolated(async (isolatedClient) => {
    const battleID = socket.battleID.split('-')[1];
    await isolatedClient.watch(battleID);
    const battleObject = await isolatedClient.HGETALL(socket.battleID);
    const userIDs = Object.keys(battleObject);
    const userValues = Object.values(battleObject);
    for (let i = 0; i < userValues.length; i += 1) {
      const { ready } = JSON.parse(userValues[i]);
      if (ready === 0) {
        delete socket.battle;
        return;
      }
    }
    if (userIDs.includes(`${socket.user.id}`)) {
      userIDs.splice(userIDs.indexOf(`${socket.user.id}`), 1);
      await Battle.deleteBattle(battleID);
      await isolatedClient.del(socket.battleID);
      console.log('battleOver');
      socket.to(socket.battleID).emit('battleTerminate', {
        reason: `${socket.user.name} just leave the battle.`,
      });
    }
  });
  delete socket.battleID;
}

module.exports = (io) => {
  ioServer = io;

  return {
    inviteBattle,
    acceptBattle,
    queryBattler,
    setReady,
    broadcastNewCodes,
    compile,
    getWinnerData,
    leaveBattle,
  };
};
