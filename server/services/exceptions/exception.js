class Exception extends Error {
  constructor(msg, log) {
    super(msg);
    this.log = log;
  }

  get fullLog() {
    return JSON.stringify({
      timestamp: new Date(),
      log: this.log,
    });
  }
}

module.exports = { Exception };
