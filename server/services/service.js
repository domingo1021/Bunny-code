require('dotenv').config();
const multer = require('multer');
const util = require('util');
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

  // Set timeout to kill sandbox which have run over 10 sec.
  const timeout = setTimeout(async () => {
    try {
      const killResult = await exec(killScript);
      const killStdout = killResult.stdout;
      const killStderr = killResult.stderr;
      console.log('kill try: ', killStderr, killStdout);
    } catch (error) {
      console.log('Kill server error: ', error);
    }
  }, threshold);

  // Execute users codes with child process.
  let stdout;
  let stderr;
  try {
    const execResult = await exec(sandboxScript);
    stdout = execResult.stdout;
    stderr = execResult.stderr;
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
  if (stderr !== undefined) {
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
  const { OOM } = JSON.parse(stdoutSplits[stdoutSplits.length - 2]);
  if (OOM) {
    throw new APIException(
      'Script runtime out of memory, please check codes again.',
      'User run code terminated with OOM',
      400,
      currentFunctionName,
    );
  }

  // if all correct, then return stdout without OOM message.
  stdoutSplits.splice(-2);
  if (stdoutSplits.length === 0) {
    return '';
  }
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

  const percentage = Math.min(
    1 - (firstCPUSecondCheck[0] - firstCPUFirstCheck[0]) / (sum(firstCPUSecondCheck) - sum(firstCPUFirstCheck)),
    1 - (secondCPUSecondCheck[0] - secondCPUFirstCheck[0]) / (sum(secondCPUSecondCheck) - sum(secondCPUFirstCheck)),
  );
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
  // Get all host from MySQL
  const hosts = await getAllHosts();

  const targetHost = await Promise.race(hosts.map(async (host) => {
    const memHealthy = await checkMemoryHealth(host);
    const cpuHealthy = await checkCPUHealth(host);
    if (memHealthy && cpuHealthy) {
      return host;
    }
  }));
  return targetHost;
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

module.exports = {
  wrapAsync, fileUploader, runCommand, compile,
};
