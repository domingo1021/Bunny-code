const express = require('express');
const { body } = require('express-validator');
const { getProjects, createProjectVersion, updateProject } = require('../controllers/project');
const { authMiddleware } = require('../services/auth');
const { validateFilter, validateNormalName } = require('../services/validation');
const { wrapAsync } = require('../services/service');

const router = express.Router();

router.route('/project/:information').get(getProjects).put(updateProject);

router.get('/project/:projectID/version/:information');

router.post(
  '/project/:projectID/version',
  authMiddleware,
  [
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
  ],
  validateFilter,
  wrapAsync(createProjectVersion),
);

module.exports = router;
