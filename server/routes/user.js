const router = require('express').Router();
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
const {
  checkPassword, checkEmail, validateNormalName, checkApplicationJSON,
} = require('../services/validation');
const {
  authMiddleware, authorization, blockNotSelf, CLIENT_CATEGORY,
} = require('../services/auth');

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

router.route('/user/:userID/project').get(getUserProjects).post(
  authMiddleware,
  authorization,
  blockNotSelf([CLIENT_CATEGORY.visitor, CLIENT_CATEGORY.otherMember]),
  [
    body('projectName').custom((projectName) => {
      if (!validateNormalName(projectName)) {
        throw new Error('Project name should only include number, alphabet, dot or _ .');
      } return true;
    }),
    body('projectDescription').custom((description) => {
      if (description.length < 1 || description.length > 30) {
        throw new Error('Project description length should be between 1 ~ 30');
      } return true;
    }),
    body('versionName').custom((versionName) => {
      if (!validateNormalName(versionName)) {
        throw new Error('Version name should only include number, alphabet, dot or _ .');
      } return true;
    }),
    body('fileName').custom((fileName) => {
      if (!validateNormalName(fileName)) {
        throw new Error('File name should only include number, alphabet, dot or _ .');
      } return true;
    }),
    body('isPublic').custom((isPublic) => {
      if (isPublic !== 0 && isPublic !== 1) {
        throw new Error('Project status must be binary.');
      } return true;
    }),
  ],
  createUserProject,
);

router.route('/user/:userID/auth').get(authMiddleware, authorization, authResponse);

router.route('/user/auth').get(authMiddleware, userIDResponse);

router.get('/user/search', getUserByName);

router.get('/user/:userID/detail', getUserDetail);

module.exports = router;
