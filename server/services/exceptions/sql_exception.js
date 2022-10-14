const { Exception } = require('./exception');

class SQLException extends Exception {
  constructor(msg, log, table, queryType, functionName) {
    super(msg, log, functionName);
    this.table = table;
    this.queryType = queryType;
  }

  get fullLog() {
    return JSON.stringify({
      ...JSON.parse(super.fullLog),
      table: this.table,
      query: this.queryType,
    });
  }
}

module.exports = { SQLException };
