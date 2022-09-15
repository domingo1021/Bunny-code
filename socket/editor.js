const pool = require('../utils/rmdb');

const versionEditStatus = async (userID, projectID, versionID) => {
  console.log(`user ${userID} is reading project-${projectID}, version-${versionID}`);
  try {
    const connection = await pool.getConnection();
    const projectSQL = 'SELECT user_id as userID FROM project WHERE project_id = ?';
    const [proejctRepsonse] = await connection.execute(projectSQL, [projectID]);
    if (userID !== proejctRepsonse[0].userID) {
      connection.release();
      return {
        readOnly: true,
        authorization: false,
      };
    }
    const versionSQL = 'SELECT editing FROM version WHERE version_id = ?';
    const [versionResponse] = await connection.execute(versionSQL, [versionID]);
    console.log('version response: ', versionResponse);
    if (versionResponse[0].editing !== 0) {
      connection.release();
      return {
        readOnly: true,
        authorization: true,
      };
    }
    const editSQL = 'UPDATE version SET editing = true WHERE version_id = ?;';
    await connection.execute(editSQL, [versionID]);
    connection.release();
    return {
      readOnly: false,
      authorization: true,
    };
  } catch (error) {
    return {
      readOnly: true,
      authorization: false,
    };
  }
};

const editVersion = async (userID, projectID, versionID) => {
  // check user id for project.
  const connection = await pool.getConnection();
  const projectSQL = 'SELECT user_id as userID FROM project WHERE project_id = ?';
  const [proejctRepsonse] = await connection.execute(projectSQL, [projectID]);
  if (userID !== proejctRepsonse[0].userID) {
    connection.release();
    return {
      kill: false,
      readOnly: false,
      authorization: false,
    };
  }
  // TODO: get project
  const getEditStatus = 'SELECT editing FROM version WHERE version_id = ?;';
  const [statusResponse] = await connection.execute(getEditStatus, [versionID]);
  console.log('statusResponse: ', statusResponse);
  if (statusResponse[0].editing) {
    connection.release();
    return [{ kill: true }, { readOnly: false, authorization: true }];
  }
  const newStatus = 1;
  const changeStatusSQL = 'UPDATE version SET editing = ? WHERE version_id = ?';
  await connection.execute(changeStatusSQL, [newStatus, versionID]);
  connection.release();
  // const versionSQL =
  return [{ kill: false }, { readOnly: false, authorization: true }];
};

const unEditing = async (versionID) => {
  const editSQL = 'UPDATE version SET editing = 0 WHERE version_id = ?';
  await pool.execute(editSQL, [versionID]);
};
module.exports = {
  editVersion,
  versionEditStatus,
  unEditing,
};
