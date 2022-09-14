const express = require('express');
// const {
//   authMiddleware, authorization, blockNotSelf, blockSelf,
// } = require('../services/auth');

const {
  getProjects, getProejctVersions, createProjectVersion, updateProject,
} = require('../controllers/project');

const router = express.Router();

// project category
// proejct search
router.get('/project/:information', getProjects);
router.route('/project/:information').get(getProjects).put(updateProject);

router.get('/project/:projectID/version/:information');

router.route('/project/:projectID/version').get(getProejctVersions).post(createProjectVersion);

module.exports = router;
