const { Exception } = require('./exception');

class APIException extends Exception {
  constructor(msg, log, status, functionName) {
    super(msg, log, functionName);
    this.status = status;
  }

  get fullLog() {
    return JSON.stringify({
      ...JSON.parse(super.fullLog),
      status: this.status,
    });
  }
}

module.exports = { APIException };
