require('dotenv').config();
const fs = require('fs').promises;
const Sandbox = require('./sandbox');

const { SANDBOX_FILE_EXTENSION } = process.env;

const questionModules = {
  'Two sum': 'twoSum',
  'Hello world': 'helloWorld',
  'Longest common subsequence': 'getLCS',
};

const testCaseEnv = {
  'Two sum': 'TWO_SUM_FILE',
  'Hello world': 'HELLO_FILE',
  'Longest common subsequence': 'LCS_FILE',
};

const testCaseScript = {
  'Two sum': 'twoSum.js',
  'Hello world': 'hello.js',
  'Longest common subsequence': 'subsequence.js',
};

class BattleValley extends Sandbox {
  constructor(host, codes, sandboxArgs) {
    const preprocessedCodes = `${codes}\n module.exports = { ${questionModules[sandboxArgs.questionName]} }`;
    super(host, preprocessedCodes);

    this.testCase = {
      env: testCaseEnv[sandboxArgs.questionName],
      script: testCaseScript[sandboxArgs.questionName],
    };
    this.containerName = `${sandboxArgs.battleID}_${sandboxArgs.battlerNumber}_${Date.now()}`;
    this.fileName = `${this.containerName}${SANDBOX_FILE_EXTENSION}`;
    this.fileDir = 'Docker/sandbox/battle_tmp_codes/';
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

  createSandboxScript() {
    return `sh server/services/shell_script/battle_sandbox.sh \
    -f ${this.fileName} \
    -d ${this.fileDir} \
    -e ${this.testCase.env} \
    -s ${this.testCase.script} \
    -c ${this.containerName} \
    -h ${this.host} \
    -i ${this.identityFile}`;
  }

  createKillScript() {
    return `sh ./server/services/shell_script/kill_container.sh \
    -c ${this.containerName} \
    -h ${this.host} \
    -i ${this.identityFile} \
    `;
  }
}

module.exports = BattleValley;

// class Workspace extends Sandbox {
//   constructor(host, codes, sandboxArgs) {
//     super(host, codes);
//     this.userID = sandboxArgs.userID;
//     this.containerName = `${sandboxArgs.userID}_${sandboxArgs.fileName}_${Date.now()}`;
//     this.fileName = `${this.containerName}${SANDBOX_FILE_EXTENSION}`;
//     this.fileDir = 'Docker/sandbox/user_tmp_codes/';
//   }

//   getContainerName() {
//     return this.containerName;
//   }

//   async saveFile() {
//     await fs.writeFile(
//       `${this.fileDir}${this.fileName}`,
//       this.codes,
//     );
//   }

//   async removeFile() {
//     await fs.unlink(
//       `${this.fileDir}${this.fileName}`,
//       super.codes,
//     );
//   }

//   createSandboxScript() {
//     return `sh server/services/shell_script/workspace_sandbox.sh \
//     -f ${this.fileName} \
//     -d ${this.fileDir} \
//     -c ${this.containerName} \
//     -h ${this.host} \
//     -i ${this.identityFile}`;
//   }

//   createKillScript() {
//     return `sh ./server/services/shell_script/kill_container.sh \
//     -c ${this.containerName} \
//     -h ${this.host} \
//     -i ${this.identityFile} \
//     `;
//   }
// }

// module.exports = Workspace;
