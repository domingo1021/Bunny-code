require('dotenv').config();
const pool = require('../../utils/rmdb');

const { DEFAULT_TEMPLATE_FILE } = process.env;

const signIn = async (userObject) => {
  const sql = 'SELECT * FROM user WHERE email = ?';
  const [signInResult] = await pool.execute(sql, [userObject.email]);
  return signInResult[0];
};

const signUp = async (userObject) => {
  const sql = 'INSERT INTO user (user_name, email, password) VALUES (?, ?, ?);';
  const [signUpResult] = await pool.execute(sql, [
    userObject.user_name,
    userObject.email,
    userObject.password]);
  return signUpResult.insertId;
};

const getUserDetail = async (email, roleId) => {
  // call api 的地方會確認 user detail 是否存在 （not null）,來判斷該 jwt user 為用戶
  try {
    if (roleId) {
      // 確認用戶的 user 存在且 Role 符合當前執行的 role ID.
      // TODO: 修改成 Role base, 除了確認 user 存不存在以外，還要看 roleID (如果是空的直接回傳空值)
      const [users] = await pool.query('SELECT id FROM user WHERE email = ?', [email]);
      if (users.length === 0) {
        return null;
      }
      const [roles] = await pool.query('SELECT id FROM user_roles WHERE user_id = ? AND role_id = ?', [users[0].id, roleId]);
      console.log(users, roles);
      return roles[0];
    }
    const [users] = await pool.query('SELECT * FROM user WHERE email = ?', [email]);
    return users[0];
  } catch (e) {
    console.log(e);
    return null;
  }
};

const getUserProjects = async (userID, category) => {
  let sql;
  if (category === 'all') {
    sql = `SELECT project_id as projectID, project_name as projectName, project_description as projectDescription,
    watch_count as watchCount, star_count as starCount, is_public as isPublic, create_at as createAt 
    FROM project
    WHERE user_id = ? AND deleted = 0
    ORDER BY create_at DESC;`;
  }
  sql = `SELECT project_id as projectID, project_name as projectName, project_description as projectDescription,
  watch_count as watchCount, star_count as starCount, is_public as isPublic, create_at as createAt 
  FROM project
  WHERE user_id = ? AND deleted = 0 AND is_public = 1
  ORDER BY create_at DESC;`;
  const [selectResponse] = await pool.execute(sql, [userID]);
  return selectResponse;
};

const createUserProject = async (projectName, projectDescription, isPublic, userID, versionName, fileName) => {
  // TODO: 預設 version, file (fileURL 使用 S3 空的 js file (default) );
  const connection = await pool.getConnection();
  await connection.beginTransaction();
  let projectID;
  try {
    const projectSQL = 'INSERT INTO project (project_name, project_description, is_public, user_id) VALUES (?, ?, ?, ?);';
    const [projectResponse] = await connection.execute(projectSQL, [projectName, projectDescription, isPublic, userID]);
    projectID = projectResponse.insertId;
    console.log('projectID = ', projectResponse.insertId);
    // TODO: create a main version where projectID is the lateset inserted projectID;
    const versionSQL = 'INSERT INTO version (version_name, version_number, project_id) VALUES (?, ?, ?)';
    const [versionResponse] = await connection.execute(versionSQL, [versionName, 1, projectResponse.insertId]);
    const fileSQL = 'INSERT INTO file (file_name, file_url, log, version_id) VALUES (?, ?, ?, ?)';
    // use file with default setting (in s3);
    await connection.execute(fileSQL, [fileName, DEFAULT_TEMPLATE_FILE, `${Date.now()}`, versionResponse.insertId]);
  } catch (error) {
    console.log(error);
    await connection.rollback();
    connection.release();
    if (error.sqlMessage.includes('Duplicate')) {
      return { msg: 'Project name already exists' };
    }
    return { msg: 'Proejct create failed' };
  }
  await connection.commit();
  connection.release();
  return { projectID };
};

module.exports = {
  signUp, signIn, getUserDetail, getUserProjects, createUserProject,
};
