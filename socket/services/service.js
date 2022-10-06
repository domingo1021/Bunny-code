const { Exception } = require('../../server/services/exceptions/exception');
const Cache = require('../../utils/cache');

function wrapAsync(cb) {
  return (socket) => {
    cb(socket).catch((err) => {
      console.log(err.message);
    });
  };
}

function checkCacheReady() {
  if (!Cache.ready) {
    throw new Exception('Internal server error', 'Redis connection error', 'checkCacheReady');
  }
}

module.exports = { wrapAsync, checkCacheReady };
