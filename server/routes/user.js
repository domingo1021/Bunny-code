const express = require('express');
const { body } = require('express-validator');
const {
  userSignUp,
  userSignIn,
  getUserProjects,
  createUserProject,
  getProejctVersions,
  createProjectVersion,
} = require('../controllers/user');
const { checkPassword, checkEmail, checkApplicationJSON } = require('../services/validation');
const {
  authMiddleware, authorization, blockNotSelf, blockSelf,
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

router.route('/user/:userID/project').get(getUserProjects).post(createUserProject);

router.route('/user/:userID/project/:projectID/version').get(getProejctVersions).post(createProjectVersion);

// router.get('/user/:userID/test-auth', authMiddleware, authorization, (req, res) => res.send({
//   id: req.user.id,
//   category: req.clientCategory,
//   user: req.user,
// }));

module.exports = router;
