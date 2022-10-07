const { Exception } = require('../../server/services/exceptions/exception');
const { SocketException } = require('../../server/services/exceptions/socketException');
const { jwtAuthenticate } = require('../../server/services/auth');
const Cache = require('../../utils/cache');

function wrapAsync(cb) {
  return async function (emitObject) {
    const socket = this;
    try {
      await cb(socket, emitObject);
    } catch (error) {
      console.log('error occur!!!!!');
      if (error instanceof Exception) {
        console.log(error.fullLog);
        if (error instanceof SocketException) {
          socket.emit(error.event);
        }
      } else if (error instanceof Error) console.log(error.stack);
    }
  };
}

function wrapMiddleware(cb) {
  return async function (socket, next) {
    try {
      await cb(socket);
      next();
    } catch (error) {
      next(error);
    }
  };
}

async function socketAuth(socket) {
  const jwtToken = socket.handshake.auth.token;
  const userPayload = await jwtAuthenticate(jwtToken);
  socket.user = userPayload;
}

function checkCacheReady() {
  if (!Cache.ready) {
    throw new Exception('Internal server error', 'Redis connection error', 'checkCacheReady');
  }
}

module.exports = {
  wrapAsync, wrapMiddleware, socketAuth, checkCacheReady,
};
