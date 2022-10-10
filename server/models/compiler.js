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
  connection.release();
};

const writeRecord = async (versionID, baseURL, startTime, endTime) => {
  const connection = await pool.getConnection();
  const selectSQL = 'SELECT * FROM record WHERE version_id = ? AND deleted = 0';
  const [selectResult] = await connection.execute(selectSQL, [versionID]);
  console.log('selectionResult: ', selectResult);
  if (selectResult.length !== 0) {
    connection.release();
    throw new Exeception('Duplicated record in a version.', 400);
  }
  const insertSQL = 'INSERT INTO record (version_id, base_url, start_time, end_time) VALUES (?, ?, ?, ?);';
  const [recordCreate] = await connection.execute(insertSQL, [versionID, baseURL, startTime, endTime]);
  connection.release();
  return recordCreate.insertId;
};

module.exports = { writeFile, writeRecord };
