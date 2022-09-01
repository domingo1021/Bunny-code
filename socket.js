const { Server } = require('socket.io');
const httpServer = require('./app');

const io = new Server(httpServer, {
  cors: {
    origin: '*', // ['https://styagram-6edf0.web.app/', 'http://localhost:3000', 'https://localhost:3000'],
    methods: ['GET', 'POST'],
    // allowedHeaders: ['my-custom-header'],
    // credentials: true,
  },
});

io.on('connection', (socket) => {
  console.log('a user connected');
  socket.on('save', (codes) => {
    console.log(codes);
  });
});
