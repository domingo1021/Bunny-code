require('dotenv').config();
const pool = require('../../utils/rmdb');
const { SQLException } = require('../services/exceptions/sql_exception');

async function getProjectByName(connection, projectName) {
  const projectSQL = `
  SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt,
  u.user_id as userID, u.user_name as userName
  FROM project as p, user as u
  WHERE project_Name = ? AND deleted = 0 AND u.user_id = p.user_id;
  `;
  const [projects] = await connection.execute(projectSQL, [projectName]);
  return projects[0];
}

async function getVersionInfo(connection, projectID, order) {
  let getVersionSQL = `
  SELECT v.version_id as versionID, v.version_name as versionName, v.version_number as versionNumber
  FROM version as v 
  WHERE project_id = ? `;
  if (order) {
    getVersionSQL += `ORDER BY v.version_id ${order}`;
  }
  const [versionInfo] = await connection.execute(getVersionSQL, [projectID]);
  return versionInfo;
}

function checkVersionExists(versionInfo, versionName) {
  for (let i = 0; i < versionInfo.length; i += 1) {
    if (versionName === versionInfo[i].versionName) {
      return true;
    }
  }
  return false;
}

async function appendProjectVersion(connection, versionInfo) {
  const currentFunctionName = 'appendProjectVersion';
  const createVersionSQL = 'INSERT INTO version (version_name, version_number, project_id) VALUES (?, ?, ?);';
  try {
    const [insertResult] = await connection.execute(createVersionSQL, [
      versionInfo.versionName,
      versionInfo.versionNumber,
      versionInfo.projectID,
    ]);
    return insertResult.insertId;
  } catch (error) {
    console.log(`Append project version error: ${error}`);
    await connection.rollback();
    connection.release();
    throw new SQLException('Version already exists.', 'Duplicated version ID', 'version', 'insert', currentFunctionName);
  }
}

async function cloneLatestFile(connection, versionID) {
  const latestFile = `
  SELECT file_name as fileName, file_url as fileUrl, log 
  FROM file
  WHERE version_id = ? AND deleted = 0 AND hided = 0
  ORDER BY file_id DESC
  `;
  const [fileResponse] = await connection.execute(latestFile, [versionID]);
  return fileResponse;
}

async function getFileDetail(connection, versionID) {
  const fileSQL = `
  SELECT file_id as fileID, file_name as fileName, file_url as fileURL, log, version_id as versionID
  FROM file
  WHERE version_id = ? AND deleted = 0 AND hided = 0
  ORDER BY file_id DESC
  `;
  const [files] = await connection.execute(fileSQL, [versionID]);
  return files[0];
}

async function appendVersionFile(connection, fileInfo) {
  const currentFunctionName = 'appendVersionFile';
  const appendFileSQL = `
  INSERT INTO file (file_name, file_url, log, version_id) VALUES (?, ?, ?, ?)`;
  try {
    const [insertResult] = await connection.execute(appendFileSQL, [
      fileInfo.fileName,
      fileInfo.fileURL,
      fileInfo.log,
      fileInfo.versionID,
    ]);
    return insertResult.insertId;
  } catch (error) {
    console.log(`Append version file error: ${error}`);
    await connection.rollback();
    connection.release();
    throw new SQLException(
      'Create file failed',
      `Unexpected result to create file for versionID=${fileInfo.versionID}`,
      'file',
      'insert',
      currentFunctionName,
    );
  }
}

async function getVersionRecord(connection, versionID) {
  const recordSQL = `
  SELECT record_id as recordID, base_url as baseURL, start_time as startTime, end_time as endTime, version_id as versionID
  FROM record
  WHERE version_id = ? AND deleted = 0;
  `;
  console.log('Version id: ', versionID);
  const [records] = await connection.execute(recordSQL, [versionID]);
  return records[0];
}

const projectDetails = async (projectName) => {
  const currentFunctionName = 'projectDetail';
  const connection = await pool.getConnection();

  // get specific project with project name.
  const project = await getProjectByName(connection, projectName);
  if (!project) {
    throw new SQLException(
      'Project not found',
      `Cannot find project with project name = ${projectName}`,
      'project',
      'select',
      currentFunctionName,
    );
  }

  // get the project's version info.
  const versions = await getVersionInfo(connection, project.projectID);
  // get file info for the version.
  const files = await Promise.all(
    versions.map(async (version) => getFileDetail(connection, version.versionID)),
  );

  const records = await Promise.all(
    versions.map(async (version) => getVersionRecord(connection, version.versionID)),
  );
  console.log('records: ', records);

  return [project, versions, files, records];
};

const searchProjects = async (keywords, paging) => {
  const sql = `SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt, 
  u.user_name as userName, u.user_id as userID
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id
  WHERE p.deleted = 0 AND p.is_public = 1 AND (p.project_name LIKE ? OR p.project_description LIKE ? OR u.user_name LIKE ?)
  ORDER BY create_at DESC
  LIMIT ? OFFSET ?
  `;
  const countSQL = `
  SELECT count(project_id) as count 
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id
  WHERE p.deleted = 0 AND is_public = 1 AND (p.project_name LIKE ? OR p.project_description LIKE ? OR u.user_name LIKE ?);`;
  const connection = await pool.getConnection();
  const likeString = `%${keywords}%`;
  const limitCount = `${paging * 6}`;
  const perPage = '6';
  const [keywordProducts] = await connection.execute(
    sql,
    [likeString, likeString, likeString, perPage, limitCount],
  );
  const [projectCounts] = await connection.execute(countSQL, [likeString, likeString, likeString]);
  const allPage = Math.ceil(projectCounts[0].count / 6);
  connection.release();
  return { projects: keywordProducts, page: paging + 1, allPage };
};

const getAllProjects = async (paging) => {
  const connection = await pool.getConnection();
  const sql = `SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt, 
  u.user_name as userName, u.user_id as userID
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id 
  WHERE p.deleted = 0 AND p.is_public = 1
  ORDER BY create_at DESC
  LIMIT ? OFFSET ?`;
  const countSQL = 'SELECT count(project_id) as count FROM project WHERE deleted = 0 AND is_public = 1;';
  const limitCount = `${paging * 6}`;
  const perPage = '6';
  const [allProject] = await connection.execute(sql, [perPage, limitCount]);
  const [projectCounts] = await connection.execute(countSQL);
  const allPage = Math.floor(projectCounts[0].count / 6) + 1;
  connection.release();
  return { projects: allProject, page: paging + 1, allPage };
};

const createProjectVersion = async (versionName, fileName, projectID) => {
  // check whether version name exists.
  const currentFunctionName = 'createProjectVersion';
  const connection = await pool.getConnection();
  await connection.beginTransaction();

  // get existing version data.
  const versionInfo = await getVersionInfo(connection, projectID, 'DESC');

  // check if version name (unique) have existed.
  const hasExisted = checkVersionExists(versionInfo, versionName);
  if (hasExisted) {
    await connection.rollback();
    connection.release();
    throw new SQLException('Version already exists.', 'Duplicated version ID', 'version', 'select', currentFunctionName);
  }

  // prepare new version number.
  let versionNumber = 1;
  if (versionInfo.length !== 0) {
    versionNumber = versionInfo[0].versionNumber + 1;
  }

  // Insert new version.
  const versionID = await appendProjectVersion(connection, {
    versionName,
    versionNumber,
    projectID,
  });
  console.log(`New version ID created: ${versionID}`);

  // Select latest file for the previous version.
  const fileInfo = await cloneLatestFile(connection, versionInfo[0].versionID);

  // Check if latest exists success.
  if (fileInfo.length === 0) {
    await connection.rollback();
    connection.release();
    throw new SQLException(
      'Cannot find base content for this project',
      `Missing project based file for project ${projectID}`,
      'file',
      'select',
      currentFunctionName,
    );
  }

  // Append a file for new Version.
  const fileID = await appendVersionFile(connection, {
    fileName,
    fileURL: fileInfo[0].fileUrl,
    log: fileInfo[0].log,
    versionID,
  });
  console.log(`New file ID created: ${fileID}`);

  await connection.commit();
  connection.release();
  const returnObject = {
    versionID,
    versionName,
    versionNumber,
    editing: 0,
    files: [{
      fileID,
      fileName,
      fileURL: process.env.AWS_DISTRIBUTION_NAME + fileInfo[0].fileUrl,
      log: fileInfo[0].log,
      versionID,
    }],
    records: [],
  };
  return returnObject;
};

const updateWatchCount = async (projectID) => {
  const sql = `
  UPDATE project SET watch_count = watch_count + 1 WHERE project_id = ?;
  `;
  await pool.execute(sql, [projectID]);
};

const updateStarCount = async (projectID) => {
  const sql = `
  UPDATE project SET star_count = star_count + 1 WHERE project_id = ?;
  `;
  await pool.execute(sql, [projectID]);
};

const getTopThreeProjects = async () => {
  const sql = `SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, 
  p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt, 
  u.user_name as userName, u.user_id as userID
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id 
  WHERE p.deleted = 0 AND p.is_public = 1
  ORDER BY watch_count DESC
  LIMIT 3`;
  const [topThree] = await pool.execute(sql);
  return topThree;
};

module.exports = {
  appendProjectVersion,
  appendVersionFile,
  searchProjects,
  getAllProjects,
  createProjectVersion,
  projectDetails,
  updateWatchCount,
  updateStarCount,
  getTopThreeProjects,
};
