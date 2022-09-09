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
    userPayload = {
      id: -1,
    };
  }
  socket.user = userPayload;
  next();
});

io.on('connection', async (socket) => {
  // authorization
  socket.on('queryBattler', async (queryObject) => {
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

  socket.on('disconnect', () => {
    console.log(`#${socket.user.id} user disconnection.`);
  });
  socket.on('error', () => {
    socket.disconnect();
  });
});
