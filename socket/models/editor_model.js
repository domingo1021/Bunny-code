const { SQLException } = require('../../server/services/exceptions/sql_exception');
const pool = require('../../utils/rmdb');

async function checkProjectOwner(connection, userID, projectID) {
  const projectSQL = 'SELECT count(user_id) as userCount FROM project WHERE user_id = ? AND project_id = ?';
  const [projectOwner] = await connection.execute(projectSQL, [userID, projectID]);
  return projectOwner[0];
}

async function getEditStatus(connection, versionID) {
  const editSQL = 'SELECT editing FROM version WHERE version_id = ?;';
  const [status] = await connection.execute(editSQL, [versionID]);
  return status[0];
}

async function updateEditStatus(connection, versionID, newStatus) {
  try {
    const editSQL = 'UPDATE version SET editing = ? WHERE version_id = ?';
    await connection.execute(editSQL, [newStatus, versionID]);
  } catch (error) {
    connection.release();
    throw new SQLException(
      'Forcely get edit authorization failed',
      `update version(version_id=${versionID}) edit status failed`,
      'editVersion',
      'version',
      'update',
    );
  }
}

async function checkProjectAtuh(userID, projectID, versionID) {
  // check user auth to specific project, return auth if available to edit.
  console.log(`user ${userID} is reading project-${projectID}, version-${versionID}`);
  const connection = await pool.getConnection();

  // check if user himself.
  const projectOwner = await checkProjectOwner(connection, userID, projectID);
  if (!projectOwner) {
    connection.release();
    return {
      readOnly: true,
      authorization: false,
    };
  }

  // check project edit status
  const editStatus = await getEditStatus(connection, versionID);
  if (editStatus?.editing) {
    connection.release();
    return {
      readOnly: true,
      authorization: true,
    };
  }

  // if available, then make it editable.
  const newStatus = 1;
  await updateEditStatus(connection, versionID, newStatus);

  connection.release();
  return {
    readOnly: false,
    authorization: true,
  };
}

const editVersion = async (userID, projectID, versionID) => {
  // Forcely edit the version, regardless of whether there is user editing.
  console.log(`user ${userID} want to forcely edit project-${projectID}, version-${versionID}`);
  const connection = await pool.getConnection();

  // check if user have the authorization.
  const projectOwner = await checkProjectOwner(connection, userID, projectID);
  if (!projectOwner) {
    connection.release();
    return [{ kill: false }, { readOnly: true, authorization: false }];
  }

  // check current version status
  const editStatus = await getEditStatus(connection, versionID);

  // if the project is editing, forcely kill the editing socket.
  if (editStatus?.editing) {
    connection.release();
    return [{ kill: true }, { readOnly: false, authorization: true }];
  }

  // edit status = 0, then let the user edit and set redis.
  const newStatus = 1;
  await updateEditStatus(connection, versionID, newStatus);
  connection.release();
  return [{ kill: false }, { readOnly: false, authorization: true }];
};
module.exports = { checkProjectAtuh, editVersion, updateEditStatus };
