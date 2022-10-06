const bcrypt = require('bcrypt');
const { createJWTtoken, jwtAuthenticate } = require('../services/auth');
const User = require('../models/user');

const userSignIn = async (req, res) => {
  const { email, password } = req.body;

  // Get user detail info and check eamil enrolled.
  const userInfo = await User.signIn(email);
  if (!userInfo) {
    return res.status(403).json({ msg: 'Authentication failed.' });
  }

  // pass bytes password into String, and campare with password input.
  const truePassword = new Buffer.from(userInfo.password).toString();
  const comparison = await bcrypt.compare(password, truePassword);

  if (!comparison) {
    return res.status(403).json({ msg: 'Authentication failed' });
  }

  // Verified, Send JWT.
  const payload = {
    id: userInfo.user_id,
    name: userInfo.user_name,
    email,
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
  const { email, password, name } = req.body;

  const user = {
    name,
    email,
  };

  // hash password.
  const saltRounds = 5;
  user.password = await bcrypt.hash(password, saltRounds);

  // sign up.
  const userID = await User.signUp(user);

  // JWT token
  const payload = {
    id: userID,
    name: user.name,
    email: user.email,
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
    // if project user himself, then get all projects; else then only return public.
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
  const { userID } = req.params;
  const {
    projectName, projectDescription, isPublic, versionName, fileName,
  } = req.body;
  if (isPublic === undefined || !projectName || !projectDescription || !versionName || !fileName) {
    return res.status(400).json({ msg: 'Lack of data.' });
  }

  // create project for user in mysql DB
  const insertResponse = await User.createUserProject(
    {
      projectName,
      projectDescription,
      isPublic: +isPublic,
    },
    +userID,
    versionName,
    fileName,
  );

  // create project success.
  console.log(`User ${req.user.id} create a project (project_id=${insertResponse.projectID}) success`);
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

const getUserDetail = async (req, res) => {
  const { userID } = req.params;

  if (!+userID) {
    return res.status(400).json({
      msg: 'Invalid user id',
    });
  }
  const userDetail = await User.getUserDetailByID(userID);

  // check if user exists.
  if (!userDetail) {
    return res.status(404).json({ msg: 'User not found.' });
  }

  // set user password undefined.
  userDetail.password = undefined;
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
  getUserDetail,
};
