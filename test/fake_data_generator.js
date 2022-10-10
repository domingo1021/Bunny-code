const pool = require('../utils/rmdb');
const { users } = require('./fake_data')(5);

// integration test create user project.
async function truncateFakeData() {
  console.log('truncating fake data');
  const truncateTable = async (table) => {
    const conn = await pool.getConnection();
    await conn.query('START TRANSACTION');
    await conn.query('SET FOREIGN_KEY_CHECKS = ?', [0]);
    await conn.query(`TRUNCATE TABLE ${table}`);
    await conn.query('SET FOREIGN_KEY_CHECKS = ?', [1]);
    await conn.query('COMMIT');
    conn.release();
  };

  const targetTables = ['user', 'project', 'version', 'file'];
  return Promise.all(targetTables.map(async (table) => truncateTable(table)));
}

async function createFakeUser(inputUsers) {
  const bulkUser = inputUsers.map((user, index) => [
    index + 1, user.name, user.email, user.password, user.picture,
  ]);
  return pool.query('INSERT INTO user (user_id, user_name, email, password, picture) VALUES ?', [
    bulkUser,
  ]);
}

async function closeConnection() {
  return pool.end();
}

async function main() {
  await truncateFakeData();
  await createFakeUser(users);
  await closeConnection();
}

// execute when called directly.
if (require.main === module) {
  console.log('main');
  main();
  console.log('truncate success');
}

module.exports = { createFakeUser, truncateFakeData, closeConnection };
