const router = require('express').Router();
const { body } = require('express-validator');
const { getProjects, createProjectVersion, updateProject } = require('../controllers/project');
const { authMiddleware } = require('../services/auth');
const { validateFilter, validateNormalName } = require('../services/validation');
const { wrapAsync } = require('../services/service');

router.route('/project/:information').get(wrapAsync(getProjects)).put(wrapAsync(updateProject));

router.post(
  '/project/:projectID/version',
  wrapAsync(authMiddleware),
  [
    body('versionName').custom((versionName) => {
      if (!validateNormalName(versionName)) {
        throw new Error('Version name should only include number, alphabet, dot or _ .');
      }
      return true;
    }),
    body('fileName').custom((fileName) => {
      if (!validateNormalName(fileName)) {
        throw new Error('File name should only include number, alphabet, dot or _ .');
      }
      return true;
    }),
  ],
  validateFilter,
  wrapAsync(createProjectVersion),
);

module.exports = router;
