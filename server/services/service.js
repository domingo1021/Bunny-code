const multer = require('multer');
const { exec } = require('child_process');
const fs = require('fs');

class FileUploadException {
  constructor(msg) {
    this.msg = msg;
  }
}

const fileUploader = multer({
  storage: multer.memoryStorage(),
  fileFilter: (req, file, cb) => {
    console.log('mimetype: ', file.mimetype);
    const fileSize = parseInt(req.headers['content-length']);
    if (file.mimetype !== 'application/javascript') {
      cb(new FileUploadException('Only javascript file is accepted'));
    } else if (fileSize >= 1024 * 1024 * 3) {
      cb(new FileUploadException('File too large.'));
    } else {
      cb(null, true);
    }
  },
}).array('files', 5);

function wrapAsync(fn) {
  return (req, res, next) => {
    fn(req, res, next).catch(next);
  };
}

async function runCommand(cmd) {
  return new Promise((resolve, reject) => {
    exec(cmd, (error, stdout, stderr) => {
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

async function compile(userID, fileName, codes) {
  const tmpTime = Date.now();
  const userCodeRoute = `./docker_tool/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  let compilerResult;
  try {
    compilerResult = await runCommand(`docker run -v \$\(pwd\)/docker_tool/user_tmp_codes:/bunny_code/user_tmp_codes --rm node-tool /bunny_code/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`);
  } catch (error) {
    compilerResult = error;
  }
  fs.rmSync(userCodeRoute);
  return compilerResult;
}

async function leetCodeCompile(battlerNumber, userID, codes, questionName) {
  const tmpTime = Date.now();
  const tmpFileName = `battle_tmp_codes/${battlerNumber}_${userID}_${tmpTime}.js`;
  const battleCodeRoute = `./docker_tool/${tmpFileName}`;
  fs.writeFileSync(battleCodeRoute, codes);
  let compilerResults;
  switch (questionName) {
    case 'Two sum': {
      try {
        compilerResults = await runCommand(`docker run -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e TWOSUMFILE=./${tmpFileName} --rm sandbox /bunny_code/twoSum.js`);
      } catch (error) {
        compilerResults = error;
      }
      break;
    }
    default:
      break;
  }
  fs.rmSync(battleCodeRoute);
  return compilerResults;
}

module.exports = {
  wrapAsync, fileUploader, FileUploadException, runCommand, compile, leetCodeCompile,
};
