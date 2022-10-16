const pool = require('../../utils/rmdb');
const { Exception } = require('../services/exceptions/exception');

async function getAllHosts() {
  const hostSQL = `
    SELECT ip_address FROM host;
  `;
  const [sqlResponse] = await pool.execute(hostSQL);

  // collect all hosts' IP;
  const hosts = sqlResponse.reduce((prev, curr) => {
    prev.push(curr.ip_address);
    return prev;
  }, []);
  console.log('Hosts: ', hosts);
  if (hosts.length === 0) {
    throw new Exception('Internal Server Error', 'Lack of IP address to arrange sandbox jobs.', 'getAllHosts');
  }
  return hosts;
}

module.exports = { getAllHosts };
