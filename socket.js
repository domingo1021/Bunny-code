const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const {
  queryBattler, getInvitations, createBattle, battleFinish, addBattleWatch, getWinnerData,
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
    // TODO: 存入 Redis, 表示某個 socket 正在編輯程式碼
    // Redis SET key = versionID, value = socketID
    console.log('status check responseObject: ', userObject);
    socket.emit('statusChecked', userObject);
  });

  socket.on('leaveWorkspace', () => {
    socket.versionID = undefined;
  });

  socket.on('unEdit', async (emitObject) => {
    console.log('socket version id: ', socket.versionID);
    // TODO: check userID and project relationship, whether user have the auth of project.
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
    const redisResult = await Cache.HSETNX(`${socket.id}`, `${socket.user.id}`, JSON.stringify(battleObject));
    setTimeout(async () => {
      // set delete hash after 20 seconds.
      console.log(`ready to delete tmp battle with socket id key ${socket.id}`);
      await Cache.HDEL(`${socket.id}`, `${socket.user.id}`);
    }, 20000);
    if (redisResult) {
      socket.broadcast.emit('userInvite', battleObject);
    }
    // await Cache.HGETALL()
    // TODO: send message to all io socket message
    // userID, name (socket.user.id, socket.user.name)發起了挑戰
    // store user define battle info into redis.
    // Battle ID 設定為一串代碼 (發起人的 socket.id ??); --> NX
    // broadcast message to all socket. 某某人(user.name)發起挑戰
    // TODO: 只有在成功放進句 Redis 時才會 emit 到其他用戶 （NX）
    // TODO: emit socket id to all user (get redis hash key);
  });

  socket.on('acceptBattle', async (emitObject) => {
    const { socketID, firstUserID } = emitObject;
    const battleObject = await Cache.HGETALL(`${socketID}`);
    if (Object.keys(battleObject)[0] !== `${firstUserID}`) {
      socket.emit('battleFailed', 'Battle accept timout, failed to create battle');
      // TODO: emit 失敗
      return;
    }
    if (firstUserID === socket.user.id) {
      socket.emit('battleFailed', 'Battler user should not be the same.');
      return;
    }
    const battlePayload = JSON.parse(battleObject[`${firstUserID}`]);
    // TODO: Create a battle in MySQL.
    const { battleID, answer } = await createBattle(battlePayload.name, battlePayload.level, battlePayload.firstUserID, socket.user.id);
    socket.battleID = `battle-${battleID}`;
    // TODO: Update redis data --> wait for two battlers to ready
    // TODO: set hash: key- battleID, field - user_1, user_2, answer, and value accordingly.
    await Cache.HDEL(`${socketID}`, `${firstUserID}`);
    const cacheObject = {};
    cacheObject[`${battlePayload.firstUserID}`] = JSON.stringify({ ready: 0, codes: '' });
    cacheObject[`${socket.user.id}`] = JSON.stringify({ ready: 0, codes: '' });
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
    // TODO: Emit to user message to go to battle (send with battle id, 讓後端直接 push 路徑到 Battle 頁面);
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

  socket.on('checkAnswer', () => {
    // TODO: check answer 剩餘次數 -1.
    // TODO: Compile user code.
    // TODO: Check user code with answer. ---> 撈 DB.
    // TODO: if (compile result === answer) ---> 發送比賽結束訊息給使用者.
    // TODO: 前端收到比賽結束 --> 上傳程式碼給後端.
  });

  // for battle
  socket.on('queryBattler', async (queryObject) => {
    socket.category = 'battle';
    // input: battleID
    console.log(`user in, with queryObject: ${JSON.stringify(queryObject)}`);
    // user join battle socket room.
    let userCategory = CLIENT_CATEGORY.visitor;
    const battleResponse = await queryBattler(queryObject.battleID);
    if (battleResponse === null) {
      console.log('Battle finished alert');
      socket.emit('battleFinished');
    }
    if ([battleResponse.firstUserID, battleResponse.secondUserID].includes(socket.user.id)) {
      userCategory = CLIENT_CATEGORY.self;
    }
    socket.battleID = `battle-${queryObject.battleID}`;
    socket.join(socket.battleID);
    let battleObject = await Cache.HGETALL(`${socket.battleID}`);
    const { firstUserID, secondUserID, answer } = battleResponse;
    // TODO: Set battle object if the battle not exists
    if (Object.keys(battleObject).length === 0) {
      const cacheObject = {};
      cacheObject[`${firstUserID}`] = JSON.stringify({ ready: 0, codes: '' });
      cacheObject[`${secondUserID}`] = JSON.stringify({ ready: 0, codes: '' });
      answer.forEach((answerObject) => {
        cacheObject[`${Object.keys(answerObject)[0]}`] = Object.values(answerObject)[0];
      });
      await Cache.HSET(`${socket.battleID}`, cacheObject);
      battleObject = cacheObject;
    }
    if (![firstUserID, secondUserID].includes(socket.user.id)) {
      await addBattleWatch(queryObject.battleID);
    }
    socket.emit('returnBattler', {
      battleResponse,
      userID: socket.user.id,
      category: userCategory,
      firstUserReady: JSON.parse(battleObject[`${battleResponse.firstUserID}`]).ready,
      secondUserReady: JSON.parse(battleObject[`${battleResponse.secondUserID}`]).ready,
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
    // TODO: 對照 compile result & answer
    // TODO: Answer for question 要先放好在 Redis 內 (JSON.stringify) --> write a CRUD question answers array function
    // TODO: 如果 Answer wrong 直接回罐頭錯誤訊息，不用給後端 stderr message.
    const battleObject = await Cache.hGetAll(`${socket.battleID}`);
    const answers = [];
    const answerIndex = [1, 2, 3, 4, 5];
    answerIndex.forEach((index) => {
      answers.push(JSON.parse(battleObject[`answer-${index}`]));
    });
    console.log(`Answers of ${queryObject.questionName}: `, answers);
    const [compilerResult, resultStatus] = await leetCodeCompile(
      queryObject.battlerNumber,
      queryObject.battleID,
      queryObject.codes,
      queryObject.questionName,
    );
    console.log(`Compile results, status: ${resultStatus} result: ${compilerResult}`);

    // const compilerResult = '6';
    if (resultStatus === 'failed') {
      console.log('failed.');
      // return
    }

    const correnctions = answers.map((answer, index) => {
      console.log('answer: ', Object.values(answer)[0]);
      console.log('result: ', JSON.parse(compilerResult)[index]);
      return Object.values(answer)[0] === JSON.parse(compilerResult)[index];
    });
    console.log(correnctions);
    socket.to(socket.battleID).emit(
      'compileDone',
      {
        battlerNumber: queryObject.battlerNumber,
        compilerResult,
      },
    );
    socket.emit('compileDone', {
      battlerNumber: queryObject.battlerNumber,
      compilerResult,
    });

    // TODO: check answer
    // compilerResult = compilerResult.split('\n');
    // compilerResult.pop();
    // compilerResult = compilerResult.reduce((prev, curr) => {
    //   prev += curr;
    //   return prev;
    // }, '');
    // if (answer.includes('[') || answer.includes('{')) {
    //   if (JSON.stringify(JSON.parse(answer)) !== JSON.stringify(JSON.parse(compilerResult))) {
    //     return;
    //   }
    // } else if (answer !== compilerResult) {
    //   return;
    // }
    // console.log(`${socket.user.name} win the game`);
    // // update battle record  --> 1. winner id & finish.
    // await battleFinish(queryObject.battleID, socket.user.id);
    // await Cache.del(`${socket.battleID}`);
    // socket.to(socket.battleID).emit('battleOver', {
    //   winnerID: socket.user.id,
    //   winnerName: socket.user.name,
    //   reason: `${socket.user.name} just compiled with the right answer`,
    // });
    // socket.emit('battleOver', {
    //   winnerID: socket.user.id,
    //   winnerName: socket.user.name,
    //   reason: 'For just compiled with the right answer',
    // });
  });

  socket.on('getWinnerData', async (queryObject) => {
    if (!queryObject.battleID) {
      return;
    }
    const winnerData = await getWinnerData(queryObject.battleID);
    socket.emit('winnerData', winnerData);
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
      // TODO: Check cache, if is battler, then battle over.
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
            if (ready === '0') {
              return;
            }
          }
          if (userIDs.includes(`${socket.user.id}`)) {
            userIDs.splice(userIDs.indexOf(`${socket.user.id}`), 1);
            const winnerUser = await battleFinish(battleID, userIDs[0]);
            console.log(winnerUser);
            await isolatedClient.del(socket.battleID);
            console.log('battleOver');
            socket.to(socket.battleID).emit('battleOver', {
              winnerID: winnerUser.winnerID,
              winnerName: winnerUser.winnerName,
              reason: `${socket.user.name} just leave the battle.`,
            });
          }
        });
      }
    }
    // check if user in battle room, otherwise, update user project status to unediting.
    console.log(`#${socket.user.id} user disconnection.`);
  });

  socket.on('error', () => {
    socket.disconnect();
  });
});
