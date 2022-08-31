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
  const userCodeRoute = `./user_tmp_codes/${userID}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  // docker run -it -v ${pwd}:/app node-tool
  // docker run -it -e CODE_FILE=/app/user_tmp_codes/1.js -v $(pwd)/user_tmp_codes:/app/user_tmp_codes node-tool
  const compilerResult = await runCommand(`docker run -e CODE_FILE=/app/user_tmp_codes/${userID}.js -v \$\(pwd\)/user_tmp_codes:/app/user_tmp_codes --rm node-log-tool`);
  res.status(200).json(compilerResult);
};

module.exports = { runCompiler };
