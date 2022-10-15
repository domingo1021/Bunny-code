const Workspace = require('./workspace');
const BattleValley = require('./battleValley');

const sandboxType = {
  workspace: Workspace,
  battleValley: BattleValley,
};

class SandboxFactory {
  constructor(type, host, codes, sandboxArgs) {
    this.type = new sandboxType[type](host, codes, sandboxArgs);
  }
}

module.exports = SandboxFactory;
