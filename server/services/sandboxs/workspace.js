require('dotenv').config();
const fs = require('fs').promises;
const Sandbox = require('./sandbox');

const { SANDBOX_FILE_EXTENSION } = process.env;

class Workspace extends Sandbox {
  constructor(host, codes, sandboxArgs) {
    super(host, codes);
    this.userID = sandboxArgs.userID;
    this.containerName = `${sandboxArgs.userID}_${sandboxArgs.fileName}_${Date.now()}`;
    this.fileName = `${this.containerName}${SANDBOX_FILE_EXTENSION}`;
    this.fileDir = 'Docker/sandbox/user_tmp_codes/';
  }

  getContainerName() {
    return this.containerName;
  }

  async saveFile() {
    await fs.writeFile(
      `${this.fileDir}${this.fileName}`,
      this.codes,
    );
  }

  async removeFile() {
    await fs.unlink(
      `${this.fileDir}${this.fileName}`,
      super.codes,
    );
  }

  createScript() {
    return `sh server/services/shell_script/workspace_sandbox.sh \
    -f ${this.fileName} \
    -d ${this.fileDir} \
    -c ${this.containerName} \
    -h ${this.host} \
    -i ${this.identityFile}`;
  }
}

module.exports = Workspace;
