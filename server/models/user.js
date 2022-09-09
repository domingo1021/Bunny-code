const pool = require('../../utils/rmdb');

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

const getUserProjects = async (userID) => {
  const sql = `SELECT project_id as projectID, project_name as projectName, 
  watch_count as watchCount, star_count as starCount, is_public as isPublic, create_at as createAt 
  FROM project
  WHERE user_id = ? AND deleted = 0;`;
  console.log(userID);
  const [selectResponse] = await pool.execute(sql, [userID]);
  return selectResponse;
};

const createUserProject = async (projectName, isPublic, userID) => {
  const sql = 'INSERT INTO project (project_name, is_public, user_id) VALUES (?, ?, ?);';
  console.log(projectName, isPublic, userID);
  const [insertResponse] = await pool.execute(sql, [projectName, isPublic, userID]);
  return insertResponse.insertId;
};

module.exports = {
  signUp, signIn, getUserDetail, getUserProjects, createUserProject,
};
