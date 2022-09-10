const pool = require('../../utils/rmdb');

const getAllProjects = async () => {
  console.log('getting all proejcts..');
  return '';
};
//  f.file_name as fileName, f.file_url as fileUrl, f.log

const createProjectVersion = async (versionName, projectID) => {
  const connection = await pool.getConnection();
  const getVersionNumber = `
  SELECT  v.version_id as versionID, v.version_number as versionNumber
  FROM version as v 
  ORDER BY v.version_id DESC
  LIMIT 1;`;
  const createVersionSQL = 'INSERT INTO version (version_name, version_number, project_id) VALUES (?, ?, ?);';
  const latestFile = `
  SELECT file_name as fileName, file_url as fileUrl, log 
  FROM file 
  WHERE version_id = ? AND deleted = 0;`;
  const defaultFile = `
  INSERT INTO file (file_name, file_url, log, version_id) VALUES (?, ?, ?, ?)`;
  const [selectResponse] = await connection.execute(getVersionNumber, [projectID]);
  let versionNumber;
  if (selectResponse.length === 0) {
    versionNumber = 1;
  } else {
    versionNumber = selectResponse[0].versionNumber + 1;
  }
  const [createResponse] = await connection.execute(createVersionSQL, [versionName, versionNumber, projectID]);
  if (selectResponse.length !== 0) {
    const [fileResponse] = await connection.execute(latestFile, [selectResponse[0].versionID]);
    console.log(fileResponse);
    if (fileResponse.length !== 0) {
      await connection.execute(defaultFile, [fileResponse[0].fileName, fileResponse[0].fileUrl, fileResponse[0].log, createResponse.insertId]);
    }
  }
  connection.release();
  return versionNumber;
};

const getProejctVeriosn = () => {
  const sql = `
  SELECT 
  `;
  return '';
};

module.exports = {
  getAllProjects,
  createProjectVersion,
  getProejctVeriosn,
};
