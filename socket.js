const { Server } = require('socket.io');
const httpServer = require('./app');
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

io.on('connection', async (socket) => {
  socket.userID = 1;
  console.log('a user connected');
  socket.on('leave workspace', async (msg) => {
    // TODO: 修正該 socket 原本連線的 project version 的 status.
    console.log('user leaving workspace.', msg);
  });
  socket.on('disconnect', () => {
    // TODO: 修正該 socket 原本連線的 project version 的 status.
    console.log(`#${socket.userID} user disconnection.`);
  });
});
