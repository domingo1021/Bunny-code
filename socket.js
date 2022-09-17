const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const {
  queryBattler, getInvitations, createBattle, battleFinish,
} = require('./socket/battle');
const { versionEditStatus, editVersion, unEditing } = require('./socket/editor');
const { getUserByName } = require('./socket/user');
const { compile } = require('./server/services/service');
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
    cacheObject[`${battlePayload.firstUserID}`] = '0';
    cacheObject[`${socket.user.id}`] = '0';
    cacheObject.answer = answer;
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
    await Cache.HSET(`${socket.battleID}`, `${emitObject.currentUserID}`, '1');
    // if (!setReady) {
    //   socket.emit('readyFailed', {
    //     failedUserID: socket.user.id,
    //     reason: 'Battle is over or authentication failed.',
    //   });
    // }
    const battleObject = await Cache.hGetAll(`${socket.battleID}`);
    socket.emit('userReady', {
      readyUserID: socket.user.id,
    });
    socket.to(`${socket.battleID}`).emit('userReady', {
      readyUserID: socket.user.id,
    });

    if (battleObject[`${emitObject.currentUserID}`] === '1' && battleObject[`${emitObject.anotherUserID}`] === '1') {
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
    if ([battleResponse.firstUserID, battleResponse.secondUserID].includes(socket.user.id)) {
      userCategory = CLIENT_CATEGORY.self;
    }
    socket.battleID = `battle-${queryObject.battleID}`;
    socket.join(socket.battleID);
    let battleObject = await Cache.HGETALL(`${socket.battleID}`);
    if (Object.keys(battleObject).length === 0) {
      const { firstUserID, secondUserID, answer } = battleResponse;
      const cacheObject = {};
      cacheObject[firstUserID] = '0';
      cacheObject[secondUserID] = '0';
      cacheObject.answer = answer;
      await Cache.HSET(`${socket.battleID}`, cacheObject);
      battleObject = cacheObject;
    }
    socket.emit('returnBattler', {
      battleResponse,
      userID: socket.user.id,
      category: userCategory,
      firstUserReady: battleObject[`${battleResponse.firstUserID}`],
      secondUserReady: battleObject[`${battleResponse.secondUserID}`],
    });
    console.log('prepare to send room msg');
    socket.to(socket.battleID).emit('in', `user #${socket.user.id} come in.`);
  });

  socket.on('newCodes', (recordObject) => {
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
    // const compilerResult = await compile(queryObject.battlerNumber, queryObject.battleID, queryObject.codes);
    // TODO: 對照 compile result & answer
    const compilerResult = '6';
    const answer = await Cache.hGet(`${socket.battleID}`, 'answer');
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
    if (answer !== compilerResult) {
      return;
    }
    console.log(`${socket.user.name} win the game`);
    // update battle record  --> 1. winner id & finish.
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
              console.log(`DB update edit status of version-${socket.versionID}`);
              await unEditing(socket.versionID);
              console.log(`redis delete socket with id ${socketID}`);
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
          console.log(userIDs, socket.user.id);
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
