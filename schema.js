const pool = require('./utils/rmdb');

// TODO: send version of code file to S3 storage.

const userTable = `
  CREATE TABLE IF NOT EXISTS user(
    user_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_name VARCHAR(30) NOT NULL,
    email NVARCHAR(255) NOT NULL,
    password BINARY(60) NOT NULL,
    follower_count INT UNSIGNED NOT NULL DEFAULT 0,
    profile VARCHAR(100) NOT NULL,
    picture VARCHAR(100),
    UNIQUE (user_name),
    UNIQUE (email),
    PRIMARY KEY (user_id)
  );
`;

const relationshipTable = `
  CREATE TABLE IF NOT EXISTS relationship(
    relationship_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    master_id INT UNSIGNED NOT NULL,
    follower_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (master_id) REFERENCES user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (follower_id) REFERENCES user(user_id) ON DELETE CASCADE,
    PRIMARY KEY (relationship_id)
  );
`;

const projectTable = `
  CREATE TABLE IF NOT EXISTS project(
    project_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_name VARCHAR(30) NOT NULL,
    watch_count INT UNSIGNED NOT NULL DEFAULT 0,
    star_count INT UNSIGNED NOT NULL DEFAULT 0,
    is_public TINYINT(1) NOT NULL,
    user_id INT UNSIGNED NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES user(user_id),
    UNIQUE (project_name),
    PRIMARY KEY (project_id)
  );
`;

const versionTable = `
  CREATE TABLE IF NOT EXISTS version(
    version_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    version_name VARCHAR(30) NOT NULL,
    project_id INT UNSIGNED NOT NULL,
    editing TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (project_id) REFERENCES project(project_id),
    PRIMARY KEY (version_id)
  );
`;

// s3 storage.
const fileTable = `
  CREATE TABLE IF NOT EXISTS file(
    file_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(30) NOT NULL,
    file_url VARCHAR(100) NOT NULL,
    version_id INT UNSIGNED NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (version_id) REFERENCES version(version_id),
    PRIMARY KEY (file_id)
  );
`;

const recordTable = `
  CREATE TABLE IF NOT EXISTS record(
    record_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    start_time datetime NOT NULL,
    end_time datetime NOT NULL,
    version_id INT UNSIGNED NOT NULL,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (version_id) REFERENCES version(version_id),
    PRIMARY KEY (record_id)
  );
`;

const streamTable = `
  CREATE TABLE IF NOT EXISTS stream(
    stream_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    viewer_counts INT UNSIGNED NOT NULL DEFAULT 0,
    project_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project(project_id),
    PRIMARY KEY (stream_id)
  );
`;

const messageTable = `
  CREATE TABLE IF NOT EXISTS chat_room(
    message_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_time DATETIME NOT NULL,
    stream_id INT UNSIGNED NOT NULL,
    FOREIGN KEY (stream_id) REFERENCES stream(stream_id),
    PRIMARY KEY (message_id)
  );
`;

const battleTable = `
  CREATE TABLE IF NOT EXISTS battle(
    battle_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    battle_name VARCHAR(30) NOT NULL,
    watch_count INT UNSIGNED NOT NULL DEFAULT 0,
    star_count INT UNSIGNED NOT NULL DEFAULT 0,
    first_user_id INT UNSIGNED NOT NULL,
    second_user_id INT UNSIGNED NOT NULL,
    is_public TINYINT(1) NOT NULL,
    winner_id INT,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (first_user_id) REFERENCES user(user_id),
    FOREIGN KEY (second_user_id) REFERENCES user(user_id),
    UNIQUE (battle_name),
    PRIMARY KEY (battle_id)
  );
`;

const userRoleTable = `

`;

const roleTable = `
`;

const rolePermissionTable = `
`;

const permissionTable = `

`;
async function createAllTable() {
  try {
    await pool.execute(userTable);
    await pool.execute(relationshipTable);
    await pool.execute(projectTable);
    await pool.execute(versionTable);
    await pool.execute(fileTable);
    await pool.execute(recordTable);
    await pool.execute(streamTable);
    await pool.execute(messageTable);
    await pool.execute(battleTable);
  } catch (error) {
    console.log(error);
  } finally {
    console.log('success');
  }
}
createAllTable();
