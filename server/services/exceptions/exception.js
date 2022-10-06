class Exception extends Error {
  constructor(msg, log, functionName) {
    super(msg);
    this.log = log;
    this.functionName = functionName;
  }

  get fullLog() {
    return JSON.stringify({
      timestamp: new Date(),
      log: this.log,
      function_name: this.functionName,
    });
  }
}

const exceptionPriority = {
  normal: 0,
  warning: 1,
  error: 2,
};

module.exports = { Exception, exceptionPriority };
