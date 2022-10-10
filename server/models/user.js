require('dotenv').config();
const pool = require('../../utils/rmdb');
const { Exception } = require('../services/exceptions/exception');
const { SQLException } = require('../services/exceptions/sql_exception');
const { appendProjectVersion, appendVersionFile } = require('./project');

const { DEFAULT_TEMPLATE_FILE } = process.env;

const signIn = async (email) => {
  const sql = 'SELECT user_id, user_name, password FROM user WHERE email = ?';
  const [essentialInfo] = await pool.execute(sql, [email]);
  return essentialInfo[0];
};

// TODO: set mysql database, user picture default value: /user/user_icon.png
const signUp = async (userObject) => {
  const currentFunctionName = 'signUp';
  const sql = 'INSERT INTO user (user_name, email, password) VALUES (?, ?, ?);';
  try {
    const [signUpResult] = await pool.execute(sql, [
      userObject.name,
      userObject.email,
      userObject.password]);
    return signUpResult.insertId;
  } catch (error) {
    if (error.sqlMessage.includes('Duplicate')) {
      throw new SQLException(
        'User name or email already exists',
        'Duplicated user name or email.',
        'user',
        'insert',
        'signUp',
      );
    }
    throw new Exception(
      'Internal server error',
      `Unknown error occur in for input ${JSON.stringify(userObject)}`,
      currentFunctionName,
    );
  }
};

const getUserDetail = async (email) => {
  const [users] = await pool.query('SELECT * FROM user WHERE email = ?', [email]);
  return users[0];
};

async function createProject(connection, projectObject, userID) {
  const currentFunctionName = 'createProject';
  const projectSQL = `
    INSERT INTO project (project_name, project_description, is_public, user_id) VALUES (?, ?, ?, ?);
  `;
  try {
    const [project] = await connection.execute(projectSQL, [
      projectObject.projectName,
      projectObject.projectDescription,
      projectObject.isPublic,
      userID,
    ]);
    return project.insertId;
  } catch (error) {
    await connection.rollback();
    connection.release();
    if (error.sqlMessage.includes('Duplicate')) {
      throw new SQLException(
        'project name already exists',
        'Duplicated project name.',
        'project',
        'insert',
        currentFunctionName,
      );
    }
    throw new SQLException(
      'Create project failed',
      `Unknown error occur in for input ${JSON.stringify(projectObject)}`,
      'project',
      'insert',
      currentFunctionName,
    );
  }
}

const getUserDetailByID = async (userID) => {
  const sql = `
  SELECT user_id as userID, user_name as userName, email, 
  follower_count as followerCount, profile, picture, level as userLevel
  FROM user
  WHERE user_id = ?
  `;
  const [userDetail] = await pool.execute(sql, [userID]);
  return userDetail[0];
};

// TODO: refactor
// 1. query string.
// 2. pool.execute.
const getUserProjects = async (userID, category, keyword, paging) => {
  let sql;
  if (category === 'all') {
    sql = `SELECT project_id as projectID, project_name as projectName, project_description as projectDescription,
    watch_count as watchCount, star_count as starCount, is_public as isPublic, create_at as createAt 
    FROM project
    WHERE user_id = ? AND deleted = 0 AND (project_name LIKE ? OR project_description LIKE ? )
    ORDER BY create_at DESC
    LIMIT ? OFFSET ?
    `;
  } else {
    sql = `SELECT project_id as projectID, project_name as projectName, project_description as projectDescription,
    watch_count as watchCount, star_count as starCount, is_public as isPublic, create_at as createAt 
    FROM project
    WHERE user_id = ? AND deleted = 0 AND is_public = 1 AND (project_name LIKE ? OR project_description LIKE ? )
    ORDER BY create_at DESC
    LIMIT ? OFFSET ?;`;
  }
  const like = `%${keyword}%`;
  const limitCount = paging * 6;
  const [selectResponse] = await pool.query(sql, [userID, like, like, 6, limitCount]);
  return selectResponse;
};

const createUserProject = async (project, userID, versionName, fileName) => {
  // create connection and transaction.
  const connection = await pool.getConnection();
  await connection.beginTransaction();

  // create project
  const projectID = await createProject(connection, project, userID);

  // create first version for the created project.
  const versionID = await appendProjectVersion(connection, {
    versionName,
    versionNumber: 1,
    projectID,
  });

  // create file with default setting (in s3);
  await appendVersionFile(connection, {
    fileName,
    fileURL: DEFAULT_TEMPLATE_FILE,
    log: Date.now(),
    versionID,
  });

  await connection.commit();
  connection.release();
  return { projectID };
};

module.exports = {
  signUp, signIn, getUserDetail, getUserProjects, createUserProject, getUserDetailByID,
};
