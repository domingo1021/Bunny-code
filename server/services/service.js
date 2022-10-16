require('dotenv').config();
const multer = require('multer');
const util = require('util');
const fs = require('fs').promises;
const exec = util.promisify(require('child_process').exec);
const { APIException } = require('./exceptions/api_exception');
const { getAllHosts } = require('../models/host');
const { Exception } = require('./exceptions/exception');
const SandboxFactory = require('./sandboxs/sandboxFactory');

const { CPU_THRESHOLD, MEM_THRESHOLD } = process.env;

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

async function runCommand(killScript, sandboxScript) {
  // Set if runtime exists.
  const currentFunctionName = 'runCommand';
  const threshold = 10000;
  const timeout = setTimeout(async () => {
    await exec(killScript);
  }, threshold);

  // Execute users codes with child process.
  let stdout;
  let stderr;
  try {
    const execResult = await exec(sandboxScript);
    stdout = execResult.stdout;
    stderr = execResult.stdout;
    clearTimeout(timeout);
  } catch (error) {
    clearTimeout(timeout);
    if (error.stderr === '') {
      await exec(killScript);
      throw new APIException(
        'Script executes timeout',
        `Runtime error with error code [${error.code}]`,
        400,
        currentFunctionName,
      );
    }
  }

  // throw if container is killed and not found due to timeout.
  if (stderr.includes('Error: No such container:')) {
    throw new APIException(
      'Script executes timeout for 10 seconds',
      'User code is terminated due to timeout.',
      400,
      'runCommand',
    );
  }

  // throw if error occured due to users' codes.
  if (stderr !== '') {
    const errMessage = stderr.split('\n').reduce((prev, curr) => {
      if (curr.includes('at') || curr.includes('bunny_code/') || curr === '') return prev;
      return `${prev}${curr}\n`;
    }, '');
    throw new APIException(
      errMessage,
      `User run code error ${errMessage}`,
      400,
      currentFunctionName,
    );
  }

  // check if terminate due to Docker OOM
  const stdoutSplits = stdout.split('\n');
  const { OOM } = JSON.parse(stdoutSplits[stdoutSplits.length - 1]);
  if (OOM) {
    throw new APIException(
      'Script runtime out of memory, please check codes again.',
      'User run code terminated with OOM',
      400,
      currentFunctionName,
    );
  }

  // if all correct, then return stdout without OOM message.
  stdoutSplits.pop();
  return stdoutSplits.reduce((prev, curr) => `${prev}\n${curr}`);
}

async function checkMemoryHealth(ipAddress) {
  try {
    const { stdout } = await exec(`sh ./server/services/shell_script/memory_metrics.sh -h ${ipAddress}`);
    const metrics = stdout.split('\n');
    const memAvailable = Number(metrics[0].split(' ')[1]);
    const memTotal = Number(metrics[1].split(' ')[1]);
    return 1 - (memAvailable / memTotal) < MEM_THRESHOLD;
  } catch (error) {
    // Something wrong for the server --> ex: record server not healthy.
    console.log(`Memory metrices for host: ${ipAddress} error: ${error.stderr}`);
    return false;
  }
}

function sum(arr) {
  return arr.reduce((prev, curr) => {
    if (curr === undefined) {
      return prev;
    }
    return Number(prev) + Number(curr);
  }, 0);
}

function cpuPercentage(stdout1, stdout2) {
  const firstCheck = stdout1.split('\n').map((element) => element.split(' ')[1]);
  const secondCheck = stdout2.split('\n').map((element) => element.split(' ')[1]);

  const firstCPUFirstCheck = firstCheck.slice(0, 8);
  const firstCPUSecondCheck = secondCheck.slice(0, 8);

  const secondCPUFirstCheck = firstCheck.slice(8);
  const secondCPUSecondCheck = secondCheck.slice(8);

  console.log(`1: ${1 - (firstCPUSecondCheck[0] - firstCPUFirstCheck[0]) / (sum(firstCPUSecondCheck) - sum(firstCPUFirstCheck))}`);
  console.log(`2: ${1 - (secondCPUSecondCheck[0] - secondCPUFirstCheck[0]) / (sum(secondCPUSecondCheck) - sum(secondCPUFirstCheck))}`);

  const percentage = Math.min(
    1 - (firstCPUSecondCheck[0] - firstCPUFirstCheck[0]) / (sum(firstCPUSecondCheck) - sum(firstCPUFirstCheck)),
    1 - (secondCPUSecondCheck[0] - secondCPUFirstCheck[0]) / (sum(secondCPUSecondCheck) - sum(secondCPUFirstCheck)),
  );
  console.log(percentage);
  return percentage;
}

async function checkCPUHealth(ipAddress) {
  try {
    const stdout1 = await exec(`sh ./server/services/shell_script/cpu_metrics.sh -h ${ipAddress}`);
    const stdout2 = await exec(`sh ./server/services/shell_script/cpu_metrics.sh -h ${ipAddress}`);
    return cpuPercentage(stdout1.stdout, stdout2.stdout) < CPU_THRESHOLD;
  } catch (error) {
    // Something wrong for the server --> ex: record server not healthy.
    console.log(`CPU metrices for host: ${ipAddress} error: ${error.stderr}`);
    return false;
  }
}

async function getAvailableHost() {
  // TODO: Cache host to Redis.
  // Get all host from MySQL  accepted
  const hosts = await getAllHosts();

  const targetHost = await Promise.race(hosts.map(async (host) => {
    const memHealthy = await checkMemoryHealth(host);
    const cpuHealthy = await checkCPUHealth(host);
    if (memHealthy && cpuHealthy) {
      return host;
    }
  }));
  console.log('target host: ', targetHost);
  return targetHost;
  // TODO: Get health metrics of EC2 server (call API to Prometheus exporter)
  // TODO: Prioritized EC2 server
  // TODO: return an assigned EC2 server to do sandbox jobs.
}

async function compile(type, codes, sandboxArgs) {
  const host = await getAvailableHost();
  if (!host) {
    throw new Exception('Bunny in too busy to run code.. try it later', 'Cannot get healthy server', 'getAvailableHost');
  }
  const sandbox = new SandboxFactory(type, host, codes, sandboxArgs).type;
  await sandbox.saveFile();
  const sandboxScript = sandbox.createSandboxScript();
  const killScript = sandbox.createKillScript();

  try {
    return await runCommand(killScript, sandboxScript);
  } finally {
    await sandbox.removeFile();
  }
}

// async function compile(userID, fileFullName, codes) {
//   const fileName = fileFullName.split(SANDBOX_FILE_EXTENSION)[0];
//   const tmpTime = Date.now();
//   const userCodeRoute = `./Docker/sandbox/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`;
//   fs.writeFileSync(userCodeRoute, codes);
//   const containerName = `${userID}_${tmpTime}`;
//   // const hostName = await
//   const shellCMD = composeShell(
//     './server/services/ssh_sandbox.sh',
//     `${userID}_${fileName}_${tmpTime}.js`,
//     containerName,
//     'user_tmp_codes',
//     hostName,
//     SSH_IDENTITY_FILE,
//   );
//   let compilerResult;
//   // TODO: file_name, target_dir, container_name, host_name
//   try {
//     compilerResult = await runCommand(
//       `${userID}_${tmpTime}`,
//       `docker run \
//       --cpus="0.2" \
//       -v \$\(pwd\)/Docker/sandbox/user_tmp_codes:/bunny_code/user_tmp_codes \
//       --rm --name ${userID}_${tmpTime} node-tool /bunny_code/user_tmp_codes/${userID}_${fileName}_${tmpTime}.js`,
//     );
//   } catch (error) {
//     console.log(error.fullLog);
//     compilerResult = error.message;
//   }
//   fs.rmSync(userCodeRoute);
//   return compilerResult;
// }

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
