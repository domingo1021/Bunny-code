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
        reject(stderr);
      }
      if (stdout) {
        resolve(stdout);
      } else {
        resolve('');
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

function preProcessCodes(codes, questionName) {
  let newCodes = codes;
  switch (questionName) {
    case 'Two sum': {
      newCodes += '\n module.exports = { twoSum };';
      break;
    }
    case 'Hello world': {
      newCodes += '\n module.exports = { helloWorld };';
      break;
    }
    case 'Longest common subsequence': {
      newCodes += '\n module.exports = { getLCS };';
      break;
    }
    default:
      break;
  }
  return newCodes;
}

async function leetCodeCompile(battlerNumber, userID, codes, questionName) {
  const processedCodes = preProcessCodes(codes, questionName);
  const tmpTime = Date.now();
  const tmpFileName = `battle_tmp_codes/${battlerNumber}_${userID}_${tmpTime}.js`;
  const battleCodeRoute = `./docker_tool/${tmpFileName}`;
  fs.writeFileSync(battleCodeRoute, processedCodes);
  let compilerResults;
  let resultStatus;
  switch (questionName) {
    case 'Two sum': {
      try {
        compilerResults = await runCommand(`docker run -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e TWO_SUM_FILE=./${tmpFileName} --rm sandbox /bunny_code/twoSum.js`);
        resultStatus = 'success';
      } catch (error) {
        compilerResults = error;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Hello world': {
      try {
        compilerResults = await runCommand(`docker run -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e HELLO_FILE=./${tmpFileName} --rm sandbox /bunny_code/hello.js`);
        resultStatus = 'success';
      } catch (error) {
        compilerResults = error;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Longest common subseauence': {
      try {
        compilerResults = await runCommand(`docker run -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e LCS_FILE=./${tmpFileName} --rm sandbox /bunny_code/subsequence.js`);
        resultStatus = 'success';
      } catch (error) {
        compilerResults = error;
        resultStatus = 'failed';
      }
      break;
    }
    default:
      break;
  }
  fs.rmSync(battleCodeRoute);
  return [compilerResults, resultStatus];
}

module.exports = {
  wrapAsync, fileUploader, FileUploadException, runCommand, compile, leetCodeCompile,
};
