const express = require('express');
const { body } = require('express-validator');
const { userSignUp, userSignIn } = require('../controllers/user');
const { checkPassword, checkEmail, checkApplicationJSON } = require('../services/validation');
const {
  jwtAuthenticate, authorization, blockNotSelf, blockSelf,
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

router.get('/user/:userID/test-auth', jwtAuthenticate, authorization, (req, res) => res.send({
  id: req.user.id,
  category: req.clientCategory,
  user: req.user,
}));

module.exports = router;
