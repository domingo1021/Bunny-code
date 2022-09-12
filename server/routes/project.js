const express = require('express');
// const {
//   authMiddleware, authorization, blockNotSelf, blockSelf,
// } = require('../services/auth');

const {
  getProjects, getProejctVersions, createProjectVersion,
} = require('../controllers/project');

const router = express.Router();

// project category
// proejct search
router.get('/project/:information', getProjects);

router.get('/project/:projectID/version/:information');

router.route('/project/:projectID/version').get(getProejctVersions).post(createProjectVersion);

module.exports = router;