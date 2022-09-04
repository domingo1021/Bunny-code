const pool = require('../../utils/rmdb');

const writeFile = async (fileName, fileURL, log, versionID) => {
  const sql = 'INSERT INTO file (file_name, file_url, log, version_id) VALUES(?, ?, ?, ?)';
  await pool.execute(sql, [fileName, fileURL, log, versionID]);
};

module.exports = { writeFile };
