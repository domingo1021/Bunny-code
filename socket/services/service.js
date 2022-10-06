const { Exception } = require('../../server/services/exceptions/exception');
const { SocketException } = require('../../server/services/exceptions/socketException');
const Cache = require('../../utils/cache');

function wrapAsync(cb) {
  return async (socket) => {
    try {
      await cb(socket);
    } catch (error) {
      if (error instanceof Exception) {
        console.log(error.fullLog);
        if (error instanceof SocketException) {
          socket.emit(error.event);
        }
      } else if (error instanceof Error) console.log(error.stack);
    }
  };
}

function checkCacheReady() {
  if (!Cache.ready) {
    throw new Exception('Internal server error', 'Redis connection error', 'checkCacheReady');
  }
}

module.exports = { wrapAsync, checkCacheReady };
