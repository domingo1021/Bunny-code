const Editor = require('../models/editor_model');
const Cache = require('../../utils/cache');
const pool = require('../../utils/rmdb');
const { checkCacheReady } = require('../services/service');

let ioServer;

async function checkProjectAuth(socket, project) {
  socket.category = 'workspace';
  let response = {
    readOnly: true,
    authorization: false,
  };
  if (!project.versionID || !project.projectID) {
    console.log(`User(id=${socket.user.id}) authrization for version(id=${project.versionID}) is ${JSON.stringify(response)}`);
    socket.emit('statusChecked', response);
    return;
  }
  response = await Editor.checkProjectAtuh(socket.user.id, project.projectID, project.versionID);
  socket.versionID = `version-${project.versionID}`;
  if (!response.readOnly) {
    if (Cache.ready) {
      await Cache.set(`${socket.versionID}`, `${socket.id}`);
    }
  }
  console.log(`User(id=${socket.user.id}) authrization for version(id=${project.versionID}) is ${JSON.stringify(response)}`);
  socket.emit('statusChecked', response);
}

async function editVersion(socket, project) {
  const viewerResponse = {
    readOnly: true,
    authorization: false,
  };

  // if data is incompleted, then early return no auth.
  if (!project.versionID || !project.projectID) {
    console.log(`User(id=${socket.user.id}) authrization for version(id=${project.versionID}) is ${JSON.stringify(viewerResponse)}`);
    return socket.emit('statusChecked', viewerResponse);
  }

  const [killObject, authObject] = await Editor.editVersion(
    socket.user.id,
    project.projectID,
    project.versionID,
  );
  console.log(`User(id=${socket.user.id}) authrization for version(id=${project.versionID}) is ${JSON.stringify(authObject)}`);

  // check redis connection.
  checkCacheReady();

  // forcely kill current editing user.
  if (killObject.kill) {
    await Cache.executeIsolated(async (isolatedClient) => {
      await isolatedClient.watch(`${socket.versionID}`);
      const socketID = await Cache.get(`${socket.versionID}`);
      if (socketID !== null) {
        console.log(`socket to kill socket with id=${socketID}`);
        ioServer.sockets.sockets.forEach((ws) => {
          if (ws.id === socketID) ws.disconnect(true);
        });
        console.log(`redis delete socket with id=${socketID}`);
        isolatedClient.del(`${socket.versionID}`);
      }
    });
  }

  // set new editing socket in redis.
  await Cache.set(`${socket.versionID}`, `${socket.id}`);
  socket.versionID = `version-${project.versionID}`;

  return socket.emit('statusChecked', authObject);
}

function leaveWorkspace(socket) {
  socket.versionID = undefined;
}

async function unEdit(socket, version) {
  console.log(`User(id=${socket.user.id}) is unediting..`);

  checkCacheReady();
  await Cache.executeIsolated(async (isolatedClient) => {
    const versionKey = `version-${version.versionID}`;
    await isolatedClient.watch(versionKey);
    const socketID = await isolatedClient.get(versionKey);
    if (socketID !== null && socketID === socket.id) {
      console.log(`User(id=${socket.user.id}) delete caching version(id=${version.versionID})`);
      isolatedClient.del(versionKey);
    }
  });

  console.log(`User(id=${socket.user.id}) unedit ${socket.versionID}`);
  const newEditStatus = 0;
  const connection = await pool.getConnection();
  await Editor.updateEditStatus(connection, version.versionID, newEditStatus);
  connection.release();
}

module.exports = (io) => {
  ioServer = io;
  return {
    editVersion,
    checkProjectAuth,
    leaveWorkspace,
    unEdit,
  };
};
