const User = require('../server/models/user');

const getUserByName = async (userName) => {
  const searchResponse = await User.getUserByName(userName);
  return searchResponse;
};

module.exports = {
  getUserByName,
};
