const multer = require('multer');
const util = require('util');
// const { exec } = require('child_process');
const exec = util.promisify(require('child_process').exec);
const fs = require('fs');

class ServiceException {
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
      cb(new ServiceException('Only javascript file is accepted'));
    } else if (fileSize >= 1024 * 1024 * 3) {
      cb(new ServiceException('File too large.'));
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

async function runCommand(containerName, cmd) {
  // Set if runtime exists.
  const threshold = 10000;
  const timeout = setTimeout(async () => {
    await exec(`docker kill ${containerName}`);
    throw new ServiceException('Script executes timeout, runtime exceeds 10 seconds.');
  }, threshold);

  // Execute users codes with child process.
  try {
    const { stdout } = await exec(cmd);
    clearTimeout(timeout);
    return stdout;
  } catch (error) {
    if (error.stderr === '') {
      throw new ServiceException(`Runtime terminated because of error code: [${error.code}]`);
    }
    clearTimeout(timeout);
    throw new ServiceException(error.stderr);
  }
}

async function compile(userID, fileName, codes) {
  const tmpTime = Date.now();
  const userCodeRoute = `./docker_tool/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  let compilerResult;
  try {
    compilerResult = await runCommand(
      `${userID}_${tmpTime}`,
      `docker run --cpus="0.2" -v \$\(pwd\)/docker_tool/user_tmp_codes:/bunny_code/user_tmp_codes --rm --name ${userID}_${tmpTime} node-tool /bunny_code/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`,
    );
  } catch (error) {
    compilerResult = error.msg;
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
        compilerResults = await runCommand(`
        docker run 
        --cpus="0.2" 
        -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} 
        -e TWO_SUM_FILE=./${tmpFileName} 
        --rm 
        sandbox 
        /bunny_code/twoSum.js`);
        resultStatus = 'success';
      } catch (error) {
        compilerResults = error;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Hello world': {
      try {
        compilerResults = await runCommand(`docker run --cpus="0.2" -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e HELLO_FILE=./${tmpFileName} --rm sandbox /bunny_code/hello.js`);
        resultStatus = 'success';
      } catch (error) {
        compilerResults = error;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Longest common subseauence': {
      try {
        compilerResults = await runCommand(`docker run --cpus="0.2" -v \$\(pwd\)/docker_tool/${tmpFileName}:/bunny_code/${tmpFileName} -e LCS_FILE=./${tmpFileName} --rm sandbox /bunny_code/subsequence.js`);
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
  wrapAsync, fileUploader, ServiceException, runCommand, compile, leetCodeCompile,
};
