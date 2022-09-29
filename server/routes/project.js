const express = require('express');
const { body } = require('express-validator');
const {
  getProjects, getProejctVersions, createProjectVersion, updateProject,
} = require('../controllers/project');
const {
  authMiddleware, authorization, blockNotSelf, CLIENT_CATEGORY,
} = require('../services/auth');
const { validateNormalName } = require('../services/validation');

const router = express.Router();

// project category
// proejct search
// router.get('/project/:information', getProjects);
router.route('/project/:information').get(getProjects).put(updateProject);

router.get('/project/:projectID/version/:information');

router.route('/project/:projectID/version').get(getProejctVersions).post(
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
  createProjectVersion,
);

module.exports = router;
