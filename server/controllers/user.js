const bcrypt = require('bcrypt');
const validator = require('express-validator');
const { createJWTtoken, jwtAuthenticate } = require('../services/auth');
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

  user.password = await bcrypt.hash(password, saltRounds);
  let userID;
  try {
    userID = await User.signUp(user);
  } catch (error) {
    if (error.sqlMessage.includes('Duplicate')) {
      return res.status(400).json({ msg: 'User name or email already exists' });
    }
  }

  // JWT token
  const payload = {
    id: userID,
    name: user.user_name,
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

const getUserProjects = async (req, res) => {
  const { userID } = req.params;
  const token = req.headers.authorization;
  let userPayload;
  try {
    userPayload = await jwtAuthenticate(token);
  } catch (error) {
    userPayload = { id: -1 };
  }
  let { keyword } = req.query;
  const paging = +req.query.paging || 0;
  if (!keyword) {
    keyword = '';
  }
  if (paging < 0) {
    return res.status(400).json({ msg: 'Invalid query string. ' });
  }
  let projects;
  try {
    if (userID === userPayload.id) {
      projects = await User.getUserProjects(userID, 'all', keyword, paging);
    } else {
      projects = await User.getUserProjects(userID, 'public', keyword, paging);
    }
  } catch (error) {
    console.log(error);
    return res.status(400).json({ msg: 'Invalid query string. ' });
  }
  return res.status(200).json({ data: projects });
};

const createUserProject = async (req, res) => {
  console.log('Createing project');
  const { userID } = req.params;
  const {
    projectName, projectDescription, isPublic, versionName, fileName,
  } = req.body;
  if (isPublic === undefined || !projectName || !projectDescription || !versionName || !fileName) {
    return res.status(400).json({ msg: 'Lack of data.' });
  }
  if (projectName.length >= 30 || projectDescription.length >= 50) {
    return res.status(400).json({ msg: 'project name or description is too long.' });
  }
  const insertResponse = await User.createUserProject(projectName, projectDescription, +isPublic, +userID, versionName, fileName);
  if (insertResponse.msg) {
    return res.status(400).json({
      ...insertResponse,
    });
  }
  return res.status(201).json({
    data: {
      ...insertResponse,
    },
  });
};

const authResponse = (req, res) => res.status(200).json({
  data: {
    clientCategory: req.clientCategory,
  },
});

const userIDResponse = (req, res) => res.status(200).json({ data: req.user.id });

const getUserByName = async (req, res) => {
  const { userName } = req.query;
  if (!userName) {
    return res.status(400).json({ msg: 'Lake of data.' });
  }
  const searchResponse = await User.getUserByName(userName);
  return res.status(200).json({
    data: searchResponse,
  });
};

const getUserDetail = async (req, res) => {
  const { userID } = req.params;
  if (!+userID) {
    return res.status(400).json({
      msg: 'Invalid user id',
    });
  }
  const userDetail = await User.getUserDetailByID(userID);
  if (!userDetail) {
    return res.status(404).json({ msg: 'User not found.' });
  }
  userDetail.picture = process.env.AWS_DISTRIBUTION_NAME + userDetail.picture;
  return res.status(200).json({
    data: userDetail,
  });
};

module.exports = {
  userSignUp,
  userSignIn,
  getUserProjects,
  createUserProject,
  authResponse,
  userIDResponse,
  getUserByName,
  getUserDetail,
};
