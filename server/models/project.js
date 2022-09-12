const pool = require('../../utils/rmdb');

const projectDetials = async (projectName) => {
  // TODO: get project basic data;
  const projectSQL = `
  SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt,
  u.user_id as userID, u.user_name as userName
  FROM project as p, user as u
  WHERE project_Name = ? AND deleted = 0 AND u.user_id = p.user_id;
  `;
  const versionSQL = `
  SELECT version_id as versionID, version_name as versionName, version_number as versionNumber, editing
  FROM version
  WHERE project_id = ? AND deleted = 0;
  `;
  const fileSQL = `
  SELECT file_id as fileID, file_name as fileName, file_url as fileURL, log, version_id as versionID
  FROM file
  WHERE version_id = ? AND deleted = 0 AND hided = 0
  ORDER BY file_id DESC
  `;
  const recordSQL = `
  SELECT record_id as recordID, start_time as startTime, end_time as endTime, version_id as versionID
  FROM record
  WHERE version_id = ? AND deleted = 0;
  `;
  const [projectResponse] = await pool.execute(projectSQL, [projectName]);
  if (projectResponse.length === 0) {
    return -1;
  }
  // TODO: get version data on projectID;
  const [versionResponse] = await pool.execute(versionSQL, [projectResponse[0].projectID]);
  // console.log(versionResponse);
  const fileResponses = await Promise.all(versionResponse.map(async (version) => {
    const [tmpFileReponse] = await pool.execute(fileSQL, [version.versionID]);
    return tmpFileReponse;
  }));
  const recordResponses = await Promise.all(versionResponse.map(async (version) => {
    const [tmpFileReponse] = await pool.execute(recordSQL, [version.versionID]);
    return tmpFileReponse;
  }));
  // console.log(fileResponses);
  // TODO: get file data on versionID;
  // TODO: get record data on versionID;
  return [projectResponse[0], versionResponse, fileResponses, recordResponses];
};

const searchProjects = async (keywords, paging) => {
  console.log(keywords, paging);
  const sql = `SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt, u.user_name as userName
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id
  WHERE p.is_public = 1 AND (p.project_name LIKE ? OR p.project_description LIKE ? OR u.user_name LIKE ?)
  ORDER BY create_at DESC
  LIMIT ? OFFSET ?
  `;
  const countSQL = `
  SELECT count(project_id) as count 
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id
  WHERE is_public = 1 AND (p.project_name LIKE ? OR p.project_description LIKE ? OR u.user_name LIKE ?);`;
  const connection = await pool.getConnection();
  const likeString = `%${keywords}%`;
  const limitCount = paging * 6;
  const [keywordProducts] = await connection.query(sql, [likeString, likeString, likeString, 6, limitCount]);
  const [projectCounts] = await connection.execute(countSQL, [likeString, likeString, likeString]);
  const allPage = Math.floor(projectCounts[0].count / 6) + 1;
  connection.release();
  return { projects: keywordProducts, page: paging + 1, allPage };
};

const getAllProjects = async (paging) => {
  const connection = await pool.getConnection();
  const sql = `SELECT p.project_id as projectID, p.project_name as projectName, p.project_description as projectDescription, p.watch_count as watchCount, p.star_count as starCount, p.create_at as createAt, u.user_name as userName
  FROM project as p
  LEFT JOIN user as u
  ON p.user_id = u.user_id 
  WHERE is_public = 1
  ORDER BY create_at DESC
  LIMIT ? OFFSET ?`;
  const countSQL = 'SELECT count(project_id) as count FROM project WHERE is_public = 1;';
  const limitCount = paging * 6;
  const [allProject] = await connection.query(sql, [6, limitCount]);
  const [projectCounts] = await connection.execute(countSQL);
  const allPage = Math.floor(projectCounts[0].count / 6) + 1;
  connection.release();
  return { projects: allProject, page: paging + 1, allPage };
};

const createProjectVersion = async (versionName, projectID) => {
  // TODO: check whether version name exists.
  const connection = await pool.getConnection();
  await connection.beginTransaction();
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
  let createResponse;
  try {
    const [createVersion] = await connection.execute(createVersionSQL, [versionName, versionNumber, projectID]);
    createResponse = createVersion;
  } catch (error) {
    console.log('create version error: ', error);
    await connection.rollback();
    return -1;
  }
  if (selectResponse.length !== 0) {
    const [fileResponse] = await connection.execute(latestFile, [selectResponse[0].versionID]);
    if (fileResponse.length !== 0) {
      try {
        await connection.execute(defaultFile, [fileResponse[0].fileName, fileResponse[0].fileUrl, fileResponse[0].log, createResponse.insertId]);
      } catch (error) {
        console.log('cloning file error: ', error);
        await connection.rollback();
        return -1;
      }
    }
  }
  await connection.commit();
  connection.release();
  return createResponse.insertId;
};

const updateVersionName = () => {

};

const updateFileName = () => {
  // TODO: S3 的檔案怎麼處理？ --> 只新增嗎？ 還是刪除？

};

const getProejctVeriosn = () => {
  const sql = `
  SELECT 
  `;
  return '';
};

module.exports = {
  searchProjects,
  getAllProjects,
  createProjectVersion,
  getProejctVeriosn,
  projectDetials,
};
