/* eslint-disable prefer-regex-literals */
const { validationResult } = require('express-validator');

function validateFilter(req, res, next) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) return res.status(400).json({ msg: errors.array() });

  return next();
}

// email varify with RegExp
const checkEmail = (email) => {
  const regex = new RegExp(/^[^\s@]+@[^\s@]+\.[^\s@]+$/);
  if (!regex.test(email)) {
    throw new Error('Invalid email format');
  } else {
    return true;
  }
};
// password check with RegExp, as least one number, one small character, and 6~30 characters long.
const checkPassword = (password) => {
  const regex = new RegExp(/^(?=.*[a-z])(?=.*\d).{6,30}$/);
  if (!regex.test(password)) {
    throw new Error('Invalid password format');
  } else {
    return true;
  }
};

// check application/json
const checkApplicationJSON = (req, res, next) => {
  if (!req.is('application/json')) {
    // Send error here
    return res.status(403).json({ msg: 'Only accept application/json' });
  }
  return next();
};

const validateNormalName = (projectName) => {
  const regex = new RegExp(/^[a-zA-Z0-9_\-.]{1,20}$/);
  // Length: 20
  if (!regex.test(projectName)) {
    return false;
  }
  return true;
};

module.exports = {
  validateFilter, checkEmail, checkPassword, validateNormalName, checkApplicationJSON,
};
