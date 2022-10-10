const Cache = require('../../utils/cache');
const pool = require('../../utils/rmdb');
const Editor = require('../models/editor_model');
const Battle = require('../models/battle_model');
const { checkCacheReady } = require('../services/service');

let ioServer;

async function leaveWorkspace(socket) {
  // TODO: if is editor, then  unedit the workspace.
  checkCacheReady();
  await Cache.executeIsolated(async (isolatedClient) => {
    await isolatedClient.watch(`${socket.versionID}`);
    const socketID = await isolatedClient.get(`${socket.versionID}`);
    if (socketID !== null) {
      if (socketID === socket.id) {
        const connection = await pool.getConnection();
        const newStatus = 0;
        await Editor.updateEditStatus(connection, socket.versionID.split('-')[1], newStatus);
        connection.release();
        isolatedClient.del(`${socket.versionID}`);
      }
    }
  });
}

async function leaveBattle(socket) {
  checkCacheReady();

  // Check cache, if is battler, then battle over.
  await Cache.executeIsolated(async (isolatedClient) => {
    const battleID = socket.battleID.split('-')[1];
    await isolatedClient.watch(battleID);
    const battleObject = await isolatedClient.HGETALL(socket.battleID);
    const userIDs = Object.keys(battleObject);
    const userValues = Object.values(battleObject);
    for (let i = 0; i < userValues.length; i += 1) {
      const { ready } = JSON.parse(userValues[i]);
      console.log('ready: ', ready);
      if (ready === 0) {
        return;
      }
    }
    if (userIDs.includes(`${socket.user.id}`)) {
      userIDs.splice(userIDs.indexOf(`${socket.user.id}`), 1);
      await Battle.deleteBattle(battleID);
      await isolatedClient.del(socket.battleID);
      console.log('battleOver');
      socket.to(socket.battleID).emit('battleTerminate', {
        reason: `${socket.user.name} just leave the battle.`,
      });
    }
  });
}

async function socketLeave(socket) {
  console.log(socket.category, socket.battleID);
  switch (socket.category) {
    case 'workspace':
      if (!socket.versionID) {
        return;
      }
      leaveWorkspace(socket);
      break;
    case 'battle':
      if (!socket.battleID) {
        return;
      }
      leaveBattle(socket);
      break;
    default:
      break;
  }
}
// // check if user in battle room, otherwise, update user project status to unediting.
// console.log(`#${socket.user.id} user disconnection.`);

module.exports = (io) => {
  ioServer = io;

  return {
    socketLeave,
  };
};
