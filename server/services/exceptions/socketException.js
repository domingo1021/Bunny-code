const { Exception } = require('./exception');

class SocketException extends Exception {
  constructor(msg, log, status, event, functionName) {
    super(msg, log, functionName);
    this.status = status;
    this.event = event;
  }
}

module.exports = { SocketException };
