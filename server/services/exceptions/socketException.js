const { Exception } = require('./exception');

class SocketException extends Exception {
  constructor(msg, log, status, event) {
    super(msg, log);
    this.status = status;
    this.event = event;
  }
}

module.exports = { SocketException };
