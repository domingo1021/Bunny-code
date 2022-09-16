const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const { queryBattler, getInvitations } = require('./socket/battle');
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
    socket.versionID = projectObject.versionID;
    if (!responseObject.readOnly) {
      if (Cache.ready) {
        await Cache.set(`${projectObject.versionID}`, `${socket.id}`);
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
        await isolatedClient.watch(`${projectObject.versionID}`);
        const socketID = await Cache.get(`${projectObject.versionID}`);
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
        await Cache.set(`${projectObject.versionID}`, `${socket.id}`);
      });
    } else if (Cache.ready) {
      await Cache.set(`${projectObject.versionID}`, `${socket.id}`);
    }
    // TODO: 存入 Redis, 表示某個 socket 正在編輯程式碼
    // Redis SET key = versionID, value = socketID
    console.log('status check responseObject: ', userObject);
    socket.emit('statusChecked', userObject);
  });

  // for battle
  socket.on('queryBattler', async (queryObject) => {
    console.log(`user in, with queryObject: ${JSON.stringify(queryObject)}`);
    socket.join(queryObject.battleID);
    const battleResponse = await queryBattler(queryObject.battleID);
    let userCategory = CLIENT_CATEGORY.visitor;
    if ([battleResponse.firstUserID, battleResponse.secondUserID].includes(socket.user.id)) {
      userCategory = CLIENT_CATEGORY.self;
    }
    // assign battle room
    socket.battleID = queryObject.battleID;
    socket.emit('returnBattler', {
      battleResponse,
      userID: socket.user.id,
      category: userCategory,
    });
    console.log('prepare to send room msg');
    socket.to(queryObject.battleID).emit('in', `user #${socket.user.id} come in.`);
  });

  socket.on('newCodes', (recordObject) => {
    socket.to(recordObject.battleID).emit('newCodes', recordObject);
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

  socket.on('getInvitations', async () => {
    if (socket.user.id !== -1) {
      const invitations = await getInvitations(socket.user.id);
      console.log('return Inviations', invitations);
      socket.emit('returnInvitations', invitations);
    }
  });

  socket.on('compile', async (queryObject) => {
    console.log(queryObject.battlerNumber, queryObject.battleID, queryObject.codes);
    const compilerResult = await compile(queryObject.battlerNumber, queryObject.battleID, queryObject.codes);
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
            await unEditing(socket.versionID);
            console.log(`redis delete socket with id ${socketID}`);
            isolatedClient.del(`${socket.versionID}`);
          }
        }
      });
    }
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
    // check if user in battle room, otherwise, update user project status to unediting.
    console.log(`#${socket.user.id} user disconnection.`);
  });

  socket.on('error', () => {
    socket.disconnect();
  });
});
