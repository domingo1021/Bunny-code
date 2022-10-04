const { Exception } = require('./exception');

class SQLException extends Exception {
  constructor(msg, log, table, queryType, functionName) {
    super(msg, log);
    this.table = table;
    this.queryType = queryType;
    this.functionName = functionName;
  }

  get fullLog() {
    return JSON.stringify({
      ...JSON.parse(super.fullLog),
      table: this.table,
      query: this.queryType,
      function: this.functionName,
    });
  }
}

module.exports = { SQLException };
