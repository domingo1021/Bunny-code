const express = require('express');
const { body } = require('express-validator');
const { getProjects, createProjectVersion, updateProject } = require('../controllers/project');
const { authMiddleware } = require('../services/auth');
const { validateFilter, validateNormalName } = require('../services/validation');
const { wrapAsync } = require('../services/service');
const { Exception } = require('../services/exceptions/exception');

const router = express.Router();

router.route('/project/:information').get(wrapAsync(getProjects))
  .put(wrapAsync(updateProject));

router.get('/project/:projectID/version/:information');

router.post(
  '/project/:projectID/version',
  authMiddleware,
  [
    body('versionName').custom((versionName) => {
      if (!validateNormalName(versionName)) {
        throw new Exception(
          'Version name should only include number, alphabet, dot or _ .',
          `Version name ${versionName} validation failed `,
        );
      }
      return true;
    }),
    body('fileName').custom((fileName) => {
      if (!validateNormalName(fileName)) {
        throw new Exception(
          'File name should only include number, alphabet, dot or _ .',
          `File name ${fileName} validation failed`,
        );
      }
      return true;
    }),
  ],
  validateFilter,
  wrapAsync(createProjectVersion),
);

module.exports = router;
