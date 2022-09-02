const { Server } = require('socket.io');
const httpServer = require('./app');
const { writeRecord, queryRecord } = require('./socket/codeRecord');

const io = new Server(httpServer, {
  cors: {
    origin: '*', // ['https://styagram-6edf0.web.app/', 'http://localhost:3000', 'https://localhost:3000'],
    methods: ['GET', 'POST'],
    // allowedHeaders: ['my-custom-header'],
    // credentials: true,
  },
});

// TODO: socket auth

io.on('connection', async (socket) => {
  console.log('a user connected');
  socket.on('save', async (codes) => {
    const userID = 1;
    const projectID = 1;
    const responseMsg = await writeRecord(userID, projectID, JSON.parse(codes));
    socket.emit('return', responseMsg);
    // const records = await queryRecord(1, 1, '2022-09-02T04:25:32.985Z', '2022-09-03T04:25:32.47Z');
    // console.log(records);
  });
  const start = `${new Date('2022-09-02T04:25:32.985Z').getTime()}000000}`;
  const end = `${new Date('2022-09-03T04:25:32.47Z').getTime()}000000}`;
  const records = await queryRecord('1', '1', start, end);
  console.log(records);
});
