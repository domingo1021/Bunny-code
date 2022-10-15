require('dotenv').config();
const multer = require('multer');
const util = require('util');
const fs = require('fs').promises;
const exec = util.promisify(require('child_process').exec);
const { APIException } = require('./exceptions/api_exception');
const { getAllHosts } = require('../models/host');
const { Exception } = require('./exceptions/exception');
const SandboxFactory = require('./sandboxs/sandboxFactory');
const Workspace = require('./sandboxs/workspace');
const BattleValley = require('./sandboxs/battleValley');

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

async function runCommand(containerName, cmd) {
  // TODO: deal with OOM
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
    const errMessage = error.stderr.split('\n').reduce((prev, curr) => {
      if (curr.includes('at') || curr.includes('bunny_code/') || curr === '') return prev;
      return `${prev}${curr}\n`;
    }, '');
    throw new APIException(errMessage, `User run code stderr error: ${errMessage}`, 400, currentFunctionName);
  }
}

function composeShell(shell, fileName, targetDir, containerName, hostName, identityName) {
  return `sh ${shell} \
  -f ${fileName} \
  -d ${targetDir} \
  -c ${containerName} \
  -h ${hostName} \
  -i ${identityName} \
  `;
}

async function checkMemoryHealth(ipAddress) {
  try {
    const { stdout } = await exec(`sh ./server/services/shell_script/memory_metrics.sh -h ${ipAddress}`);
    const metrics = stdout.split('\n');
    const memAvailable = Number(metrics[0].split(' ')[1]);
    const memTotal = Number(metrics[1].split(' ')[1]);
    return 1 - (memAvailable / memTotal) < MEM_THRESHOLD;
  } catch (error) {
    // Something wrong for the server.
    console.log(`Memory metrices for host: ${ipAddress} error: ${error.stderr}`);
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
  const metrics1 = stdout1.split('\n').map((element) => element.split(' ')[1]);
  const metrics2 = stdout2.split('\n').map((element) => element.split(' ')[1]);
  const percentage = 1 - (metrics2[0] - metrics1[0]) / (sum(metrics2) - sum(metrics1));
  console.log(percentage);
  return percentage;
}

async function checkCPUHealth(ipAddress) {
  try {
    const stdout1 = await exec(`sh ./server/services/shell_script/cpu_metrics.sh -h ${ipAddress}`);
    const stdout2 = await exec(`sh ./server/services/shell_script/cpu_metrics.sh -h ${ipAddress}`);
    return cpuPercentage(stdout1.stdout, stdout2.stdout) < CPU_THRESHOLD;
  } catch (error) {
    console.log(`CPU metrices for host: ${ipAddress} error: ${error.stderr}`);
  }
}

async function getAvailableHost() {
  // TODO: Cache host to Redis.
  // Get all host from MySQL  accepted
  const hosts = await getAllHosts();

  for (let i = 0; i < hosts.length; i += 1) {
    const memHealthy = await checkMemoryHealth(hosts[i]);
    const cpuHealthy = await checkCPUHealth(hosts[i]);
    if (memHealthy && cpuHealthy) {
      return hosts[i];
    }
  }
  throw new Exception('Internal Server Error', 'Cannot get available server', 'getAvailableHost');
  // TODO: Get health metrics of EC2 server (call API to Prometheus exporter)
  // TODO: Prioritized EC2 server
  // TODO: return an assigned EC2 server to do sandbox jobs.
}

async function compile(type, codes, sandboxArgs) {
  const host = await getAvailableHost();
  const sandbox = new SandboxFactory(type, host, codes, sandboxArgs).type;
  await sandbox.saveFile();
  const shellScript = sandbox.createScript();
  const containerName = sandbox.getContainerName();

  try {
    return await runCommand(containerName, shellScript);
  } catch (error) {
    console.log(error.fullLog);
    return error.message;
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
