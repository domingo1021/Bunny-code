require('dotenv').config();
const { Server } = require('socket.io');
const { httpServer } = require('./app');

const { WHITE_LIST } = process.env;
const io = new Server(httpServer, {
  path: '/api/socket/',
  cors: {
    origin: WHITE_LIST,
    methods: ['GET', 'POST'],
    credentials: true,
  },
});

const { wrapAsync, wrapMiddleware, socketAuth } = require('./socket/services/service');
const Battle = require('./socket/controllers/battle_controller')(io);
const Editor = require('./socket/controllers/editor_controller')(io);
const WS = require('./socket/controllers/socket_controller')(io);

io.use(wrapMiddleware(socketAuth));

io.on('connection', (socket) => {
  console.log(`User(id=${socket.user.id}) with socket(id=${socket.id}) come in`);
  // for workspace.
  socket.on('checkProjectStatus', wrapAsync(Editor.checkProjectAuth));
  socket.on('changeEdit', wrapAsync(Editor.editVersion));
  socket.on('leaveWorkspace', wrapAsync(Editor.leaveWorkspace));
  socket.on('unEdit', wrapAsync(Editor.unEdit));

  // for battle.
  socket.on('inviteBattle', wrapAsync(Battle.inviteBattle));
  socket.on('acceptBattle', wrapAsync(Battle.acceptBattle));
  socket.on('setReady', wrapAsync(Battle.setReady));
  socket.on('queryBattler', wrapAsync(Battle.queryBattler));
  socket.on('newCodes', wrapAsync(Battle.broadcastNewCodes));
  socket.on('compile', wrapAsync(Battle.battleCompile));
  socket.on('getWinnerData', wrapAsync(Battle.getWinnerData));
  socket.on('leaveBattle', wrapAsync(Battle.leaveBattle));

  // for normal use.
  socket.on('disconnect', wrapAsync(WS.socketLeave));
  socket.on('error', () => {
    socket.disconnect();
  });
});
