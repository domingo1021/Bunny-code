const Workspace = require('./workspace');
const BattleValley = require('./battleValley');

const sandboxType = {
  workspace: Workspace,
  battleValley: BattleValley,
};

class SandboxFactory {
  static createSandbox(type, host, codes, sandboxArgs) {
    return new sandboxType[type](host, codes, sandboxArgs);
  }
}

module.exports = SandboxFactory;
