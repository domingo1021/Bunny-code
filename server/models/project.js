const pool = require('../../utils/rmdb');

const getAllProjects = async () => {
  console.log('getting all proejcts..');
  return '';
};

const createProjectVersion = async (versionName, projectID) => {
  const connection = await pool.getConnection();
  const getVersionNumber = 'SELECT version_number as versionNumber FROM version WHERE project_id = ? ORDER BY version_id DESC LIMIT 1;';
  const createVersionSQL = 'INSERT INTO version (version_name, version_number, project_id) VALUES (?, ?, ?);';
  const [selectResponse] = await connection.execute(getVersionNumber, [projectID]);
  let versionNumber;
  if (selectResponse.length === 0) {
    versionNumber = 1;
  } else {
    versionNumber = selectResponse[0].versionNumber + 1;
  }
  const [createResponse] = await connection.execute(createVersionSQL, [versionName, versionNumber, projectID]);
  connection.release();
  return versionNumber;
};

const getProejctVeriosn = () => {
  console.log('123');
  return '';
};

module.exports = {
  getAllProjects,
  createProjectVersion,
  getProejctVeriosn,
};
