const jwt = require('jsonwebtoken');
const User = require('../models/user');
const { APIException } = require('./exceptions/api_exception');

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

const jwtAuthenticate = async (token) => {
  const currentFunctionName = 'jwtAuthenticate';
  const jwtToken = token && token.split(' ')[1];
  if (jwtToken === 'null' || !jwtToken) {
    throw new APIException('Please Login', '401, User authentication failed with no token', 401, currentFunctionName);
  }
  const decoded = await new Promise((resolve, reject) => {
    jwt.verify(jwtToken, process.env.JWT_SECRET_KEY, (err, auth) => {
      if (err) {
        // If for more specific: if want to track user lifecycle, keep user id to further funciotn.
        return reject(new APIException('Please Login', `403, JWT token ${token} authentication failed`, 403, currentFunctionName));
      }
      return resolve(auth);
    });
  });
  return decoded.payload;
};

const authMiddleware = async (req, res, next) => {
  const token = req.headers.authorization;
  const user = await jwtAuthenticate(token);
  req.user = user;
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
  console.log(`User id=${userDetail.user_id} call auth api, with params userID=${userID}`);

  req.user.id = userDetail.user_id;
  if (userDetail.user_id === +userID) {
    req.clientCategory = CLIENT_CATEGORY.self;
    return next();
  }
  req.clientCategory = CLIENT_CATEGORY.otherMember;
  return next();
};

function blockNotSelf(blockCategories) {
  return (req, res, next) => {
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
  createJWTtoken,
  authMiddleware,
  jwtAuthenticate,
  authorization,
  blockNotSelf,
  blockSelf,
  CLIENT_CATEGORY,
};
