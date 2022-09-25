const router = require('express').Router();
const { fileUploader } = require('../services/service');
const { authMiddleware } = require('../services/auth');
const {
  runCompiler, getFiles, writeRecord, queryRecord, writeFile, writeBattleFile,
} = require('../controllers/compiler');
const uploadS3 = require('../services/cloudStorage');

router.post('/compiler', runCompiler);

router.route('/record').post(writeRecord);

router.route('/history/:projectID').post(queryRecord);

router.route('/record/file').get(getFiles).post(authMiddleware, fileUploader, uploadS3, writeFile);
router.route('/record/battle').post(authMiddleware, fileUploader, uploadS3, writeBattleFile);

module.exports = router;
