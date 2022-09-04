const bcrypt = require('bcrypt');
const validator = require('express-validator');
const { createJWTtoken } = require('../services/auth');
const User = require('../models/user');

const userSignIn = async (req, res) => {
  const errors = validator.validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({ errors: errors.array() });
  }

  const { email, password } = req.body;
  let userInfo;
  try {
    userInfo = await User.signIn({ email });
  } catch (error) {
    return res.status(401).json({ msg: 'Email not enrolled.' });
  }

  if (userInfo.Picture == null) {
    userInfo.Picture = undefined;
  }

  // pass bytes password into String
  const truePassword = new Buffer.from(userInfo.password).toString();

  // compare user password with the one in database with bcrypt.
  const comparison = await bcrypt.compare(password, truePassword);
  userInfo.Password = undefined;
  if (!comparison) {
    return res.status(403).json({ msg: 'Authentication failed' });
  }
  // Verified, Send JWT.
  const payload = {
    id: userInfo.user_id,
    name: userInfo.user_name,
    email,
    picture: userInfo.picture,
  };
  const exp = 360000;
  const jwtToken = await createJWTtoken(payload, exp);
  return res.status(200).json({
    data: {
      access_token: jwtToken,
      access_expired: exp,
      user: {
        id: userInfo.user_id,
        ...payload,
      },
    },
  });
};

const userSignUp = async (req, res) => {
  const errors = validator.validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({ errors: errors.array() });
  }

  const { email, password, name } = req.body;

  const user = {
    user_name: name,
    email,
  };

  const saltRounds = 5;

  // 1. hash password with bcrypt.
  user.password = await bcrypt.hash(password, saltRounds);
  let userID;
  try {
    // TODO: 記得修改 execte
    userID = await User.signUp(user);
  } catch (error) {
    if (error.sqlMessage.includes('Duplicate')) {
      return res.json({ msg: 'User name or email already exists' });
    }
  }

  // JWT token
  const payload = {
    id: userID,
    name: user.name,
    email: user.email,
    provider: 'native',
  };
  const exp = 360000; // 3600 ms
  const jwtToken = await createJWTtoken(payload, exp);

  return res.json({
    data: {
      access_token: jwtToken,
      access_expired: exp,
      user: {
        id: userID,
        ...payload,
      },
    },
  });
};

module.exports = { userSignUp, userSignIn };
