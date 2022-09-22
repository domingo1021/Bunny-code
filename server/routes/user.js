const express = require('express');
const { body } = require('express-validator');
const {
  userSignUp,
  userSignIn,
  getUserProjects,
  createUserProject,
  authResponse,
  userIDResponse,
  getUserByName,
  getUserDetail,
} = require('../controllers/user');
const { checkPassword, checkEmail, checkApplicationJSON } = require('../services/validation');
const {
  authMiddleware, authorization, blockNotSelf, blockSelf, CLIENT_CATEGORY,
} = require('../services/auth');

const router = express.Router();

router.post(
  '/user/signup',
  checkApplicationJSON,
  [
    body('name').not().isEmpty(),
    body('email')
      .not().isEmpty().custom((email) => checkEmail(email)),
    body('password')
      .not().isEmpty().custom((password) => checkPassword(password)),
  ],
  checkApplicationJSON,
  userSignUp,
);

router.post(
  '/user/signin',
  [
    body('email')
      .not().isEmpty().custom((email) => checkEmail(email)),
    body('password')
      .not().isEmpty().custom((password) => checkPassword(password)),
  ],
  checkApplicationJSON,
  userSignIn,
);

router.route('/user/:userID/project').get(getUserProjects).post(authMiddleware, authorization, blockNotSelf([CLIENT_CATEGORY.visitor, CLIENT_CATEGORY.otherMember]), createUserProject);

router.route('/user/:userID/auth').get(authMiddleware, authorization, authResponse);

router.route('/user/auth').get(authMiddleware, userIDResponse);

router.get('/user/search', getUserByName);

router.get('/user/:userID/detail', getUserDetail);

module.exports = router;
