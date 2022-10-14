const router = require('express').Router();
const { body } = require('express-validator');
const {
  userSignUp,
  userSignIn,
  getUserProjects,
  createUserProject,
  authResponse,
  userIDResponse,
  getUserDetail,
} = require('../controllers/user');
const {
  checkPassword, checkEmail, validateNormalName, checkApplicationJSON, validateFilter,
} = require('../services/validation');
const {
  authMiddleware, authorization, blockNotSelf, CLIENT_CATEGORY,
} = require('../services/auth');
const { wrapAsync } = require('../services/service');

router.post(
  '/user/signin',
  [
    body('email')
      .not().isEmpty().custom((email) => checkEmail(email)),
    body('password')
      .not().isEmpty().custom((password) => checkPassword(password)),
  ],
  checkApplicationJSON,
  validateFilter,
  wrapAsync(userSignIn),
);

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
  validateFilter,
  wrapAsync(userSignUp),
);

router.route('/user/:userID/project').get(getUserProjects).post(
  wrapAsync(authMiddleware),
  authorization,
  blockNotSelf([CLIENT_CATEGORY.visitor, CLIENT_CATEGORY.otherMember]),
  [
    body('projectName')
      .not()
      .isEmpty()
      .custom((projectName) => {
        if (!validateNormalName(projectName)) {
          throw new Error('Project name should only include number, alphabet, dot or _ .');
        } return true;
      }),
    body('projectDescription')
      .not()
      .isEmpty()
      .custom((description) => {
        if (description.length < 1 || description.length > 30) {
          throw new Error('Project description length should be between 1 ~ 30');
        } return true;
      }),
    body('versionName')
      .not()
      .isEmpty()
      .custom((versionName) => {
        if (!validateNormalName(versionName)) {
          throw new Error('Version name should only include number, alphabet, dot or _ .');
        } return true;
      }),
    body('fileName')
      .not()
      .isEmpty()
      .custom((fileName) => {
        if (!validateNormalName(fileName)) {
          throw new Error('File name should only include number, alphabet, dot or _ .');
        } return true;
      }),
    body('isPublic')
      .not()
      .isEmpty()
      .custom((isPublic) => {
        if (isPublic !== 0 && isPublic !== 1) {
          throw new Error('Project status must be binary.');
        } return true;
      }),
  ],
  validateFilter,
  wrapAsync(createUserProject),
);

router.route('/user/:userID/auth').get(wrapAsync(authMiddleware), authorization, authResponse);

router.route('/user/auth').get(wrapAsync(authMiddleware), userIDResponse);

router.get('/user/:userID/detail', getUserDetail);

module.exports = router;
