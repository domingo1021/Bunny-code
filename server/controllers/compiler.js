const { exec } = require('child_process');
const fs = require('fs');

const runCompiler = async (req, res) => {
  const { userID } = req.body;
  const { codes } = req.body;
  async function runCommand(cmd) {
    let result;
    exec(cmd, (error, stdout, stderr) => {
      if (error) {
        console.log(`error: ${error.message}`);
        return;
      }
      if (stderr) {
        console.log(`stderr: ${stderr}`);
        return;
      }
      result = stdout;
    });
    return result;
  }
  const userCodeRoute = `./user_tmp_codes/${userID}`;
  fs.writeFileSync(userCodeRoute, codes);
  const compilerResult = await runCommand(`docker run -v ${userCodeRoute}:user_codes.js --rm node-log-tool`);
  res.status(200).json(compilerResult);
};

module.exports = { runCompiler };
