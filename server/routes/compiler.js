const router = require('express').Router();
const { fileUploader } = require('../services/service');
const { authMiddleware } = require('../services/auth');
const {
  runCompiler, getFiles, writeRecord, queryRecord, writeFile,
} = require('../controllers/compiler');
const uploadS3 = require('../services/cloudStorage');

router.post('/compiler', runCompiler);

router.route('/record').post(writeRecord);

router.route('/history/:userID').post(queryRecord);

router.route('/record/file').get(getFiles).post(authMiddleware, fileUploader, uploadS3, writeFile);

module.exports = router;
