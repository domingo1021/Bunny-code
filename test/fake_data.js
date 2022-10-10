const bcrypt = require('bcrypt');

const saltRounds = 5;
let dataThreshold;

const users = [
  {
    email: 'test1@gmail.com',
    password: bcrypt.hashSync('test1password', saltRounds),
    name: 'test1',
    picture: '/user/user_icon.png',
  },
  {
    email: 'test2@gmail.com',
    password: bcrypt.hashSync('test2password', saltRounds),
    name: 'test2',
    picture: '/user/user_icon.png',
  },
  {
    email: 'test3@gmail.com',
    password: bcrypt.hashSync('test3password', saltRounds),
    name: 'test3',
    picture: '/user/user_icon.png',
  },
];

const projects = [
  {
    projectName: 'hello_domingo',
    projectDescription: 'hello everyone',
    isPublic: 1,
  },
  {
    projectName: 'hello_domingo2',
    projectDescription: 'hello everyone',
    isPublic: 0,
  },
  {
    projectName: 'hello_domingo3',
    projectDescription: 'hello everyone',
    isPublic: 0,
  },
  {
    projectName: 'hello_domingo4',
    projectDescription: 'hello everyone',
    isPublic: 1,
  },
  {
    projectName: 'hello_domingo5',
    projectDescription: 'hello everyone',
    isPublic: 0,
  },
];

function generateVersion(versionName) {
  return {
    versionName: `${versionName}`,
    fileName: 'test.js',
  };
}

function fakeVersionGenerate() {
  const projectVersion = [];
  for (let i = 0; i < dataThreshold; i += 1) {
    projectVersion.push(generateVersion(`version_${i}`));
  }
  return projectVersion;
}

module.exports = (maxAmount) => {
  dataThreshold = maxAmount;
  return {
    users,
    projects,
    fakeVersionGenerate,
  };
};
