const { Exception } = require('./exception');

class APIException extends Exception {
  constructor(msg, log, status, apiName) {
    super(msg, log);
    this.status = status;
    this.apiName = apiName;
  }
}

module.exports = { APIException };
