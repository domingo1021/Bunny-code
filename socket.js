const { Server } = require('socket.io');
const httpServer = require('./app');
const { jwtAuthenticate, AuthenticationError } = require('./server/services/auth');
const { authorization, CLIENT_CATEGORY } = require('./socket/util');
const { queryBattler } = require('./socket/battle');
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
    userPayload = undefined;
  }
  socket.user = userPayload;
  next();
});

io.on('connection', async (socket) => {
  console.log(`a user connected, payload: ${JSON.stringify(socket.user)}`);
  // authorization
  socket.on('queryBattler', async (queryObject) => {
    const battlers = await queryBattler(queryObject.battleID);
    let userCategory = CLIENT_CATEGORY.visitor;
    if (battlers.includes(socket.user.id)) {
      userCategory = CLIENT_CATEGORY.self;
    }
    console.log({
      battlers: [battlers[0], battlers[1]],
      category: userCategory,
    });
    socket.emit('returnBattler', {
      battlers: [battlers[0], battlers[1]],
      userID: socket.user.id,
      category: userCategory,
    });
  });
  socket.on('disconnect', () => {
    console.log(`#${socket.user.id} user disconnection.`);
  });
  socket.on('error', () => {
    socket.disconnect();
  });
});
