const pool = require('../../utils/rmdb');
const Exeception = require('../services/execption');

const writeFile = async (fileName, fileURL, log, versionID) => {
  const connection = await pool.getConnection();
  const updateFileStatus = `
    UPDATE file SET hided = 1 WHERE version_id = ? AND file_name = ?
  `;
  const insertSQL = 'INSERT INTO file (file_name, file_url, log, version_id) VALUES(?, ?, ?, ?)';
  await connection.execute(updateFileStatus, [versionID, fileName]);
  await connection.execute(insertSQL, [fileName, fileURL, log, versionID]);
};

const writeRecord = async (versionID, baseURL, startTime, endTime) => {
  const connection = await pool.getConnection();
  const selectSQL = 'SELECT * FROM record WHERE version_id = ? AND deleted = 0';
  const [selectResult] = await connection.execute(selectSQL, [versionID]);
  console.log('selectionResult: ', selectResult);
  if (selectResult.length !== 0) {
    throw new Exeception('Duplicated record in a version.', 400);
  }
  const insertSQL = 'INSERT INTO record (version_id, base_url, start_time, end_time) VALUES (?, ?, ?, ?);';
  const [recordCreate] = await connection.execute(insertSQL, [versionID, baseURL, startTime, endTime]);
  connection.release();
  return recordCreate.insertId;
};

const getFiles = async (versionID) => {
  const sql = `
  SELECT file_name as fileName, file_url as fileUrl, log FROM file WHERE version_id = ?;
  `;
  const [files] = await pool.execute(sql, [versionID]);
  return files;
};

module.exports = { writeFile, writeRecord, getFiles };
