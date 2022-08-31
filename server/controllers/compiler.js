const { exec } = require('child_process');
const fs = require('fs');

const runCompiler = async (req, res) => {
  const { userID } = req.body;
  const { codes } = req.body;
  async function runCommand(cmd) {
    return new Promise((resolve, reject) => {
      exec(cmd, (error, stdout, stderr) => {
        if (error) {
          console.log(`error: ${error.message}`);
          reject(error);
        }
        if (stderr) {
          console.log(`stderr: ${stderr}`);
          reject(stderr);
        }
        if (stdout) {
          console.log(`stdout: ${stdout}`);
          resolve(stdout);
        }
      });
    });
  }
  const userCodeRoute = `./user_tmp_codes/${userID}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  const compilerResult = await runCommand(`docker run -e CODE_FILE=/app/user_tmp_codes/${userID}.js -v \$\(pwd\)/user_tmp_codes:/app/user_tmp_codes --rm node-tool`);
  fs.rmSync(userCodeRoute);
  return res.status(200).json(compilerResult);
};

module.exports = { runCompiler };
