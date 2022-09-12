const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const { queryBattler } = require('./socket/battle');
const { versionEditStatus, editVersion, unEditing } = require('./socket/editor');
const { compile } = require('./server/services/service');
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
    if (!responseObject.readOnly) {
      socket.versionID = projectObject.versionID;
    }
    console.log(responseObject);
    socket.emit('statusChecked', responseObject);
  });

  // TODO: have to disconnect user who has been editing the version;
  socket.on('changeEdit', async (projectObject) => {
    let responseObject = {
      readOnly: true,
      authorization: false,
    };
    if (socket.user.id === -1 || !projectObject.versionID || !projectObject.projectID) {
      socket.emit('statusChecked', responseObject);
      return;
    }
    responseObject = await editVersion(socket.user.id, projectObject.projectID, projectObject.versionID);
    socket.emit('statusChecked', responseObject);
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
  // TODO: "on" get new records from socket, boardcast records to the room of user.

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

  socket.on('disconnect', async () => {
    if (socket.category === 'workspace' && socket.versionID !== undefined) {
      await unEditing(socket.versionID);
    }
    // check if user in battle room, otherwise, update user project status to unediting.
    console.log(`#${socket.user.id} user disconnection.`);
  });

  socket.on('error', () => {
    socket.disconnect();
  });
});
