const { Exception } = require('./exception');

class SocketException extends Exception {
  constructor(msg, log, status, event, functionName) {
    super(msg, log, functionName);
    this.status = status;
    this.event = event;
  }

  get fullLog() {
    return JSON.stringify({
      ...JSON.parse(super.fullLog),
      status: this.status,
      event: this.event,
    });
  }
}

module.exports = { SocketException };
