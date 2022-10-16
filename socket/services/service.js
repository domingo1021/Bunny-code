const { Exception } = require('../../server/services/exceptions/exception');
const { SocketException } = require('../../server/services/exceptions/socketException');
const { jwtAuthenticate } = require('../../server/services/auth');
const Cache = require('../../utils/cache');
const { APIException } = require('../../server/services/exceptions/api_exception');

const CLIENT_CATEGORY = {
  visitor: 0,
  otherMember: 1,
  self: 2,
};

const authorization = (userID, userPayload) => {
  if (!userPayload) {
    return CLIENT_CATEGORY.visitor;
  }
  if (userID !== userPayload) {
    return CLIENT_CATEGORY.otherMember;
  }
  return CLIENT_CATEGORY.self;
};

function wrapAsync(cb) {
  return async function (emitObject) {
    const socket = this;
    try {
      await cb(socket, emitObject);
    } catch (error) {
      if (error instanceof Exception) {
        console.log(error.fullLog);
        if (error instanceof SocketException) {
          return socket.emit(error.event);
        }
      } else if (error instanceof Error) console.log(error.stack);
      return socket.emit('Internal server error');
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
  wrapAsync,
  wrapMiddleware,
  socketAuth,
  checkCacheReady,
  CLIENT_CATEGORY,
  authorization,
};
