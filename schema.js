const pool = require('./utils/rmdb');

// TODO: send version of code file to S3 storage.

const userTable = `
  CREATE TABLE IF NOT EXISTS user(
    user_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    user_name VARCHAR(30) NOT NULL,
    email NVARCHAR(255) NOT NULL,
    password BINARY(60) NOT NULL,
    follower_count INT NOT NULL UNSIGNED DEFAULT 0,
    profile VARCHAR(100) NOT NULL,
    picture VARCHAR(100),
    UNIQUE (Email),
    PRIMARY KEY (user_id)
  );
`;

const relationshipTable = `
  CREATE TABLE IF NOT EXISTS relationship(
    relation_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    master_id INT NOT NULL UNSIGNED,
    follower_id INT NOT NULL UNSIGNED,
    FOREIGN KEY (master_id) REFERENCES user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (follower_id) REFERENCES user(user_id) ON DELETE CASCADE,
    PRIMARY KEY (relationship_id)
  );
`;

const projectTable = `
  CREATE TABLE IF NOT EXISTS project(
    project_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    project_name VARCHAR(30) NOT NULL,
    watch_count INT NOT NULL UNSIGNED DEFAULT 0,
    star_count INT NOT NULL UNSIGNED DEFAULT 0,
    is_public TINYINT(1) NOT NULL,
    user_id INT NOT NULL UNSIGNED,
    FOREIGN KEY (follower_id) REFERENCES user(user_id) ON DELETE SET NULL,
    PRIMARY KEY (project_id)
  );
`;

const versionTable = `
  CREATE TABLE IF NOT EXISTS version(
    version_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    version_name VARCHAR(30) NOT NULL,
    project_id INT NOT NULL UNSIGNED,
    FOREIGN KEY (project_id) REFERENCES project(project_id) ON DELETE SET NULL,
    PRIMARY KEY (version_id)
  );
`;

// s3 storage.
const fileTable = `
  CREATE TABLE IF NOT EXISTS file(
    file_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    file_name VARCHAR(30) NOT NULL,
    file_url VARCHAR(100) NOT NULL,
    version_id INT NOT NULL UNSIGNED,
    FOREIGN KEY (version_id) REFERENCES version(version_id) ON DELETE SET NULL,
    PRIMARY KEY (file_id)
  );
`;

const recordTable = `
  CREATE TABLE IF NOT EXISTS record(
    record_id INT NOT NULL UNSIGNED AUTO_INCREMENT,
    start_time timestamp NOT NULL,
    end_time timestamp NOT NULL,
    version_id INT NOT NULL UNSIGNED,
    FOREIGN KEY (version_id) REFERENCES version(version_id) ON DELETE SET NULL,
    PRIMARY KEY record_id)
  );
`;

const streamTable = `

`;

const chatroomTable = `
`;

const battleTable = `

`;

const userRoleTable = `

`;

const roleTable = `
`;

const rolePermissionTable = `
`;

const permissionTable = `
`;
