const multer = require('multer');
const util = require('util');
const fs = require('fs');
const exec = util.promisify(require('child_process').exec);
const { APIException } = require('./exceptions/api_exception');

const fileUploader = multer({
  storage: multer.memoryStorage(),
  fileFilter: (req, file, cb) => {
    console.log('mimetype: ', file.mimetype);
    const fileSize = parseInt(req.headers['content-length']);
    if (file.mimetype !== 'application/javascript') {
      cb(new APIException(
        'Only javascript file is accepted',
        `Unexpected mimetype = ${file.mimetype} trying to upload.`,
        400,
        'multer',
      ));
    } else if (fileSize >= 1024 * 1024 * 3) {
      cb(new APIException(
        'File too large.',
        `Unexpected file size = ${fileSize} too large.`,
        400,
        'multer',
      ));
    } else {
      cb(null, true);
    }
  },
}).array('files', 5);

// wrap async function with try.. ctach..
function wrapAsync(fn) {
  return (req, res, next) => {
    fn(req, res, next).catch(next);
  };
}

// send console log into event queue
// do the console.log only when call stack is clean.
function setLog(msg) {
  setTimeout(() => {
    console.log(msg);
  }, 0);
}

async function runCommand(containerName, cmd) {
  // Set if runtime exists.
  const currentFunctionName = 'runCommand';
  const threshold = 10000;
  const timeout = setTimeout(async () => {
    await exec(`docker kill ${containerName}`);
    throw new APIException(
      'Script executes timeout, runtime exceeds 10 seconds.',
      `User code ${cmd} is terminated due to timeout.`,
      400,
      currentFunctionName,
    );
  }, threshold);

  // Execute users codes with child process.
  try {
    const { stdout } = await exec(cmd);
    clearTimeout(timeout);
    return stdout;
  } catch (error) {
    if (error.stderr === '') {
      throw new APIException(
        'Script executes timeout',
        `Runtime error with error code [${error.code}], for code = ${cmd}`,
        400,
        currentFunctionName,
      );
    }
    clearTimeout(timeout);
    const errMessage = error.stderr.split('\n').reduce((prev, curr) => { console.log(curr)
      if (curr.includes('at') || curr.includes('bunny_code/') || curr==="") return prev;
      return `${prev}${curr}\n`;
    }, '');
    throw new APIException(errMessage, `User run code stderr error: ${errMessage}`, 400, currentFunctionName);
  }
}

async function compile(userID, fileName, codes) {
  const tmpTime = Date.now();
  const userCodeRoute = `./Docker/sandbox/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`;
  fs.writeFileSync(userCodeRoute, codes);
  let compilerResult;
  try {
    compilerResult = await runCommand(
      `${userID}_${tmpTime}`,
      `docker run \
      --cpus="0.2" \
      -v \$\(pwd\)/Docker/sandbox/user_tmp_codes:/bunny_code/user_tmp_codes \
      --rm --name ${userID}_${tmpTime} node-tool /bunny_code/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`,
    );
  } catch (error) {
    console.log(error.fullLog);
    compilerResult = error.message;
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
  const containerName = `${battlerNumber}_${userID}_${tmpTime}`;
  const tmpFileName = `battle_tmp_codes/${battlerNumber}_${userID}_${tmpTime}.js`;
  const battleCodeRoute = `./Docker/sandbox/${tmpFileName}`;
  fs.writeFileSync(battleCodeRoute, processedCodes);
  let compilerResults;
  let resultStatus;
  switch (questionName) {
    case 'Two sum': {
      try {
        compilerResults = await runCommand(
          containerName,
          `docker run \
          --cpus="0.2" \
          -v \$\(pwd\)/Docker/sandbox/${tmpFileName}:/bunny_code/${tmpFileName} \
          -e TWO_SUM_FILE=./${tmpFileName} \
          --name ${containerName} \
          --rm sandbox \
          /bunny_code/twoSum.js`,
        );
        resultStatus = 'success';
      } catch (error) {
        console.log(error.fullLog);
        compilerResults = error.message;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Hello world': {
      try {
        compilerResults = await runCommand(
          containerName,
          `docker run \
          --cpus="0.2" \
          -v \$\(pwd\)/Docker/sandbox/${tmpFileName}:/bunny_code/${tmpFileName} \
          -e HELLO_FILE=./${tmpFileName} \
          --name ${containerName} \
          --rm sandbox \
          /bunny_code/hello.js`,
        );
        resultStatus = 'success';
      } catch (error) {
        console.log(error.fullLog);
        compilerResults = error.message;
        resultStatus = 'failed';
      }
      break;
    }
    case 'Longest common subsequence': {
      try {
        compilerResults = await runCommand(
          containerName,
          `docker run \
          --cpus="0.2" \
          -v \$\(pwd\)/Docker/sandbox/${tmpFileName}:/bunny_code/${tmpFileName} \
          -e LCS_FILE=./${tmpFileName} \
          --name ${containerName} \
          --rm sandbox \
          /bunny_code/subsequence.js`,
        );
        resultStatus = 'success';
      } catch (error) {
        console.log(error.fullLog);
        compilerResults = error.message;
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
  wrapAsync, fileUploader, runCommand, compile, leetCodeCompile,
};
