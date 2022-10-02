const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const {
  queryBattler, createBattle, battleFinish, addBattleWatch, getWinnerData, deleteBattle,
} = require('./socket/battle');
const { versionEditStatus, editVersion, unEditing } = require('./socket/editor');
const { getUserByName } = require('./socket/user');
const { compile, leetCodeCompile } = require('./server/services/service');
const Cache = require('./utils/cache');
// const { writeRecord, queryRecord } = require('./server/controllers/codeRecord');

const io = new Server(httpServer, {
  path: '/api/socket/',
  cors: {
    origin: '*',
    methods: ['GET', 'POST'],
    credentials: true,
  },
});
// TODO: socket auth with middleware

io.use(async (socket, next) => {
  // authentication user token;
  const jwtToken = socket.handshake.auth.token;
  let userPayload;
  try {
    userPayload = await jwtAuthenticate(jwtToken);
  } catch (error) {
    console.log('error: ', error);
    userPayload = {
      id: -1,
      name: 'visitor',
    };
  }
  socket.user = userPayload;
  next();
});
// TODO: 當 connection 時，需辨認進入的 socket 種類，Editor 的部分也需要使用到 socket (表示使用者正在編輯);
// TODO: 如果是本人進入頁面（認為想要 edit）, 則建立 Socket, 並更動 edit 狀態，

io.on('connection', async (socket) => {
  console.log(`socketID: ${socket.id} come in`);
  // for workspace
  socket.on('checkProjectStatus', async (projectObject) => {
    console.log(`user #${socket.user.id} connecting...`);
    socket.category = 'workspace';
    let responseObject = {
      readOnly: true,
      authorization: false,
    };
    if (socket.user.id === -1 || !projectObject.versionID || !projectObject.projectID) {
      socket.emit('statusChecked', responseObject);
      return;
    }
    responseObject = await versionEditStatus(socket.user.id, projectObject.projectID, projectObject.versionID);
    socket.versionID = `version-${projectObject.versionID}`;
    if (!responseObject.readOnly) {
      if (Cache.ready) {
        await Cache.set(`${socket.versionID}`, `${socket.id}`);
      }
    }
    console.log(responseObject);
    socket.emit('statusChecked', responseObject);
  });

  // TODO: have to disconnect user who has been editing the version;
  socket.on('changeEdit', async (projectObject) => {
    const viewerResponse = {
      readOnly: true,
      authorization: false,
    };
    if (socket.user.id === -1 || !projectObject.versionID || !projectObject.projectID) {
      socket.emit('statusChecked', viewerResponse);
      return;
    }
    const [killObject, userObject] = await editVersion(socket.user.id, projectObject.projectID, projectObject.versionID);
    if (killObject.kill) {
      await Cache.executeIsolated(async (isolatedClient) => {
        await isolatedClient.watch(`${socket.versionID}`);
        const socketID = await Cache.get(`${socket.versionID}`);
        if (socketID !== null) {
          console.log(`ready to kill a socket ${socketID}`);
          io.sockets.sockets.forEach((ws) => {
            if (ws.id === socketID) { ws.disconnect(true); }
          });
          if (socketID === socket.id) {
            console.log(`redis delete socket with id ${socketID}`);
            isolatedClient.del(`${socket.versionID}`);
          }
        }
        await Cache.set(`${socket.versionID}`, `${socket.id}`);
      });
    } else if (Cache.ready) {
      await Cache.set(`${socket.versionID}`, `${socket.id}`);
    }
    socket.versionID = `version-${projectObject.versionID}`;
    // 存入 Redis, 表示某個 socket 正在編輯程式碼
    // Redis SET key = versionID, value = socketID
    console.log('status check responseObject: ', userObject);
    socket.emit('statusChecked', userObject);
  });

  socket.on('leaveWorkspace', () => {
    socket.versionID = undefined;
  });

  socket.on('unEdit', async (emitObject) => {
    console.log('socket version id: ', socket.versionID);
    // check userID and project relationship, whether user have the auth of project.
    if (Cache.ready) {
      await Cache.executeIsolated(async (isolatedClient) => {
        await isolatedClient.watch(`${socket.versionID}`);
        const socketID = await isolatedClient.get(`${socket.versionID}`);
        console.log('socketID: ', socketID, socket.id);
        if (socketID !== null) {
          if (socketID === socket.id) {
            console.log(`DB update edit status of version-${socket.versionID}`);
            await unEditing(emitObject.versionID);
            console.log(`redis delete socket with id ${socketID}`);
            isolatedClient.del(`${socket.versionID}`);
          }
        }
      });
    }
  });

  socket.on('inviteBattle', async (emitObject) => {
    const battleObject = {
      socketID: socket.id,
      name: emitObject.battleName,
      level: emitObject.battleLevel,
      firstUserID: socket.user.id,
      firstUserName: socket.user.name,

    };
    const redisGet = await Cache.hGetAll(`${socket.id}`);
    if (Object.keys(redisGet)[0]) {
      console.log(`User ${socket.id} too many request !`);
      socket.emit('inviteFailed', 'Pleas wait for 10 seconds for another invitation.');
      return;
    }
    const redisResult = await Cache.HSETNX(`${socket.id}`, `${socket.user.id}`, JSON.stringify(battleObject));
    setTimeout(async () => {
      // set delete hash after 10 seconds.
      console.log(`ready to delete tmp battle with socket id key ${socket.id}`);
      await Cache.HDEL(`${socket.id}`, `${socket.user.id}`);
    }, 10000);
    if (redisResult) {
      io.emit('userInvite', battleObject);
    }
  });

  socket.on('acceptBattle', async (emitObject) => {
    const { socketID, firstUserID } = emitObject;
    const battleObject = await Cache.HGETALL(`${socketID}`);
    if (Object.keys(battleObject)[0] !== `${firstUserID}`) {
      // emit 失敗
      socket.emit('battleFailed', 'Battle accept timout, failed to create battle');
      return;
    }
    if (firstUserID === socket.user.id) {
      socket.emit('battleFailed', 'Battler user should not be the same.');
      return;
    }
    const battlePayload = JSON.parse(battleObject[`${firstUserID}`]);
    // TODO: Create a battle in MySQL.
    const { battleID, answer, created } = await createBattle(battlePayload.name, battlePayload.level, battlePayload.firstUserID, socket.user.id);
    if (!created) {
      socket.emit('battleFailed', 'Battle name already exist.');
      return;
    }
    socket.battleID = `battle-${battleID}`;
    // TODO: Update redis data --> wait for two battlers to ready
    // TODO: set hash: key- battleID, field - user_1, user_2, answer, and value accordingly.
    await Cache.HDEL(`${socketID}`, `${firstUserID}`);
    const cacheObject = {};
    cacheObject[`${battlePayload.firstUserID}`] = JSON.stringify({ ready: 0, codes: '', chance: 3 });
    cacheObject[`${socket.user.id}`] = JSON.stringify({ ready: 0, codes: '', chance: 3 });
    answer.forEach((answerObject) => {
      cacheObject[`${Object.keys(answerObject)[0]}`] = Object.values(answerObject)[0];
    });
    const cacheBattleResult = await Cache.HSET(`${socket.battleID}`, cacheObject);
    if (cacheBattleResult) {
      io.to(socketID).emit('battleCreated', {
        battleID,
      });
      socket.emit('battleCreated', {
        battleID,
      });
    }
  });

  socket.on('setReady', async (emitObject) => {
  // { battleID, currentUserID, anotherUserID }
    if (emitObject.currentUserID !== socket.user.id) {
      return;
    }
    // TODO: Send ready state to redis hash data where the battle id live in.
    const battlerInfo = await Cache.HGET(`${socket.battleID}`, `${emitObject.currentUserID}`);
    if (battlerInfo === null) {
      return;
    }
    const battlerObject = JSON.parse(battlerInfo);
    battlerObject.ready = '1';
    await Cache.HSET(`${socket.battleID}`, `${emitObject.currentUserID}`, JSON.stringify(battlerObject));
    socket.emit('userReady', {
      readyUserID: socket.user.id,
    });
    socket.to(`${socket.battleID}`).emit('userReady', {
      readyUserID: socket.user.id,
    });

    const allBattler = await Cache.hGetAll(`${socket.battleID}`);
    if (JSON.parse(allBattler[`${emitObject.currentUserID}`]).ready === '1' && JSON.parse(allBattler[`${emitObject.anotherUserID}`]).ready === '1') {
      socket.to(`${socket.battleID}`).emit('battleStart');
      socket.emit('battleStart');
    }
  });

  socket.on('queryBattler', async (queryObject) => {
    socket.category = 'battle';
    console.log(`user in, with queryObject: ${JSON.stringify(queryObject)}`);
    const battleResponse = await queryBattler(queryObject.battleID);
    // battle not found.
    if (battleResponse === null) {
      socket.emit('battleNotFound');
      return;
    }
    // battle have already finished, redirect.
    if (Object.keys(battleResponse).length === 0) {
      console.log('Battle finished.');
      socket.emit('battleFinished');
      return;
    }
    // check user status.
    let userCategory = CLIENT_CATEGORY.visitor;
    if ([battleResponse.firstUserID, battleResponse.secondUserID].includes(socket.user.id)) {
      userCategory = CLIENT_CATEGORY.self;
    }
    // input: battleID
    socket.battleID = `battle-${queryObject.battleID}`;
    // user join battle socket room.
    socket.join(socket.battleID);
    const battleObject = await Cache.HGETALL(`${socket.battleID}`);
    const { firstUserID, secondUserID, answer } = battleResponse;
    // TODO: Set battle object if the battle not exists
    if (Object.keys(battleObject).length === 0) {
      socket.emit('battleNotFound');
      return;
    }
    if (![firstUserID, secondUserID].includes(socket.user.id)) {
      await addBattleWatch(queryObject.battleID);
    }
    socket.emit('returnBattler', {
      battleResponse,
      userID: socket.user.id,
      category: userCategory,
      firstUserObject: {
        ready: JSON.parse(battleObject[`${battleResponse.firstUserID}`]).ready,
        codes: JSON.parse(battleObject[`${battleResponse.firstUserID}`]).codes,
        chance: JSON.parse(battleObject[`${battleResponse.firstUserID}`]).chance,
      },
      secondUserObject: {
        ready: JSON.parse(battleObject[`${battleResponse.secondUserID}`]).ready,
        codes: JSON.parse(battleObject[`${battleResponse.secondUserID}`]).codes,
        chance: JSON.parse(battleObject[`${battleResponse.secondUserID}`]).chance,
      },
    });
    console.log('prepare to send room msg');
    socket.to(socket.battleID).emit('in', `user #${socket.user.id} come in.`);
  });

  socket.on('newCodes', async (recordObject) => {
    await Cache.executeIsolated(async (isolatedClient) => {
      await isolatedClient.watch(socket.battleID);
      const battlerInfo = await isolatedClient.HGET(`${socket.battleID}`, `${socket.user.id}`);
      const battlerObject = JSON.parse(battlerInfo);
      battlerObject.codes = recordObject.newCodes;
      await isolatedClient.HSET(`${socket.battleID}`, `${socket.user.id}`, JSON.stringify(battlerObject));
    });
    socket.to(socket.battleID).emit('newCodes', recordObject);
  });

  socket.on('searchUsers', async (userName) => {
    if (!userName) {
      socket.emit('responseUsers', ({
        msg: 'Lake of data',
      }));
    }
    const searchResponse = await getUserByName(userName);
    socket.emit('responseUsers', searchResponse);
  });

  socket.on('compile', async (queryObject) => {
    const battleObject = await Cache.hGetAll(`${socket.battleID}`);
    const currentUserObject = JSON.parse(battleObject[`${socket.user.id}`]);
    console.log('Current User Object: ', currentUserObject);
    if (currentUserObject.chance === 0) {
      return;
    }
    currentUserObject.chance -= 1;
    // Set back object to Redis cache.
    await Cache.HSET(`${socket.battleID}`, `${socket.user.id}`, JSON.stringify(currentUserObject));
    const answers = [];
    const answerIndex = [1, 2, 3, 4, 5];
    answerIndex.forEach((index) => {
      answers.push(JSON.parse(battleObject[`answer-${index}`]));
    });
    const [compilerResult, resultStatus] = await leetCodeCompile(
      queryObject.battlerNumber,
      queryObject.battleID,
      queryObject.codes,
      queryObject.questionName,
    );

    // User limit count.
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
      console.log('error: ', error);
      corrections.push(false);
    }
    // build the object that will send to frontend for correction display.
    // Send user the test case which is wrong.
    console.log('correction: ', corrections);
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
      await battleFinish(queryObject.battleID, socket.user.id);
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
      await deleteBattle(queryObject.battleID);
      await Cache.del(`${socket.battleID}`);
      socket.to(socket.battleID).emit('battleTerminate', {
        reason: `${socket.user.name} just ran out of compile chance`,
      });
      socket.emit('battleTerminate', {
        reason: `${socket.user.name} just ran out of compile chance`,
      });
    }
  });

  socket.on('getWinnerData', async (queryObject) => {
    if (!queryObject.battleID) {
      return;
    }
    const winnerData = await getWinnerData(queryObject.battleID);
    if (winnerData === null) {
      socket.emit('battleNotFound');
    }
    socket.emit('winnerData', winnerData);
  });

  socket.on('leaveBattle', async () => {
    if (!Cache.ready) {
      return;
    }
    await Cache.executeIsolated(async (isolatedClient) => {
      const battleID = socket.battleID.split('-')[1];
      await isolatedClient.watch(battleID);
      const battleObject = await isolatedClient.HGETALL(socket.battleID);
      const userIDs = Object.keys(battleObject);
      const userValues = Object.values(battleObject);
      for (let i = 0; i < userValues.length; i += 1) {
        const { ready } = JSON.parse(userValues[i]);
        console.log('ready: ', ready);
        if (ready === 0) {
          delete socket.battle;
          return;
        }
      }
      if (userIDs.includes(`${socket.user.id}`)) {
        userIDs.splice(userIDs.indexOf(`${socket.user.id}`), 1);
        await deleteBattle(battleID);
        await isolatedClient.del(socket.battleID);
        console.log('battleOver');
        socket.to(socket.battleID).emit('battleTerminate', {
          reason: `${socket.user.name} just leave the battle.`,
        });
      }
    });
    delete socket.battle;
  });

  socket.on('disconnect', async () => {
    if (socket.category === 'workspace' && socket.versionID !== undefined) {
      // TODO: cahce 撈 versionID 的 socketID 去比對離開的人的 socket.id (如果相同則 unEditing)
      if (Cache.ready) {
        await Cache.executeIsolated(async (isolatedClient) => {
          await isolatedClient.watch(`${socket.versionID}`);
          const socketID = await isolatedClient.get(`${socket.versionID}`);
          if (socketID !== null) {
            if (socketID === socket.id) {
              await unEditing(socket.versionID.split('-')[1]);
              isolatedClient.del(`${socket.versionID}`);
            }
          }
        });
      }
    }
    console.log(socket.category, socket.battleID);
    if (socket.category === 'battle' && socket.battleID !== undefined) {
      // Check cache, if is battler, then battle over.
      if (Cache.ready) {
        await Cache.executeIsolated(async (isolatedClient) => {
          const battleID = socket.battleID.split('-')[1];
          await isolatedClient.watch(battleID);
          const battleObject = await isolatedClient.HGETALL(socket.battleID);
          const userIDs = Object.keys(battleObject);
          const userValues = Object.values(battleObject);
          for (let i = 0; i < userValues.length; i += 1) {
            const { ready } = JSON.parse(userValues[i]);
            console.log('ready: ', ready);
            if (ready === 0) {
              return;
            }
          }
          if (userIDs.includes(`${socket.user.id}`)) {
            userIDs.splice(userIDs.indexOf(`${socket.user.id}`), 1);
            await deleteBattle(battleID);
            await isolatedClient.del(socket.battleID);
            console.log('battleOver');
            socket.to(socket.battleID).emit('battleTerminate', {
              reason: `${socket.user.name} just leave the battle.`,
            });
          }
        });
      }
    }
    // // check if user in battle room, otherwise, update user project status to unediting.
    // console.log(`#${socket.user.id} user disconnection.`);
  });

  socket.on('error', () => {
    socket.disconnect();
  });
});
