const jwt = require('jsonwebtoken');
const User = require('../models/user');

// Promise sign
const createJWTtoken = (payload, exp) => new Promise((resolve, reject) => {
  jwt.sign(
    { payload, expiresIn: exp },
    process.env.JWT_SECRET_KEY,
    (err, token) => {
      if (err) {
        return reject(err);
      }
      return resolve(token);
    },
  );
});

// Promise verify
const jwtAuthenticate = async (req, res, next) => {
  const header = req.headers.authorization;
  const jwtToken = header && header.split(' ')[1];
  if (jwtToken === 'null' || !jwtToken) {
    return res.status(401).json({ msg: 'Please provide token' });
  }
  try {
    const decoded = await new Promise((resolve, reject) => {
      jwt.verify(jwtToken, process.env.JWT_SECRET_KEY, (err, token) => {
        if (err) {
          return reject(err);
        }
        return resolve(token);
      });
    });
    req.user = decoded.payload;
    console.log(decoded.payload);
  } catch (error) {
    return res.status(403).send({ msg: 'Token authorization failed.' });
  }
  return next();
};

const CLIENT_CATEGORY = {
  visitor: 0,
  otherMember: 1,
  self: 2,
};

const authorization = async (req, res, next) => {
  const { userID } = req.params;
  if (userID === undefined) {
    return res.status(400).json({ error: 'Pleas provide a user id.' });
  }
  let accessToken = req.get('Authorization');
  if (!accessToken) {
    req.clientCategory = CLIENT_CATEGORY.visitor;
    return next();
  }

  accessToken = accessToken.replace('Bearer ', '');
  if (accessToken === 'null') {
    req.clientCategory = CLIENT_CATEGORY.visitor;
    return next();
  }

  // Check JWT and URL params userID
  const { user } = req;
  const userDetail = await User.getUserDetail(user.email);
  if (!userDetail) {
    req.clientCategory = CLIENT_CATEGORY.visitor;
    return next();
  }

  req.user.id = userDetail.user_id;
  if (userDetail.id === +userID) {
    req.clientCategory = CLIENT_CATEGORY.self;
    return next();
  }
  req.clientCategory = CLIENT_CATEGORY.otherMember;
  return next();
};

function blockNotSelf(blockCategories) {
  return (req, res, next) => {
    console.log('User category: ', req.clientCategory);
    if (
      blockCategories.includes(CLIENT_CATEGORY.visitor)
      && req.clientCategory === CLIENT_CATEGORY.visitor
    ) {
      return res.status(401).send({ error: 'Unauthorized' });
    }
    if (
      blockCategories.includes(CLIENT_CATEGORY.otherMember)
      && req.clientCategory === CLIENT_CATEGORY.otherMember
    ) {
      return res.status(403).send({ error: 'Forbidden' });
    }
    return next();
  };
}

const blockSelf = (req, res, next) => {
  if (req.clientCategory === CLIENT_CATEGORY.self) {
    return res.status(403).json({ error: 'Forbidden' });
  }
  return next();
};

module.exports = {
  createJWTtoken, jwtAuthenticate, authorization, blockNotSelf, blockSelf,
};
