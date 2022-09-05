const pool = require('../../utils/rmdb');

const writeFile = async (fileName, fileURL, log, versionID) => {
  const sql = 'INSERT INTO file (file_name, file_url, log, version_id) VALUES(?, ?, ?, ?)';
  await pool.execute(sql, [fileName, fileURL, log, versionID]);
};

const writeRecord = async (versionID, startTime, endTime) => {
  const sql = 'INSERT INTO record (version_id, start_time, end_time) VALUES (?, ?, ?);';
  await pool.execute(sql, [versionID, startTime, endTime]);
};

module.exports = { writeFile, writeRecord };
