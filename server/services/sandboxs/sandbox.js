require('dotenv').config();

const { SSH_IDENTITY_FILE } = process.env;

class Sandbox {
  constructor(host, codes) {
    this.host = host;
    this.codes = codes;
    this.identityFile = SSH_IDENTITY_FILE;
  }
}

module.exports = Sandbox;
