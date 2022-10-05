const router = require('express').Router();
const { fileUploader, wrapAsync } = require('../services/service');
const { authMiddleware } = require('../services/auth');
const {
  runCompiler, writeRecord, queryRecord, writeFile, writeBattleFile,
} = require('../controllers/compiler');
const uploadS3 = require('../services/cloudStorage');

router.post('/compiler', runCompiler);

router.route('/record').post(writeRecord);

router.route('/history/:projectID').post(wrapAsync(queryRecord));

router.route('/record/file').post(wrapAsync(authMiddleware), fileUploader, uploadS3, writeFile);
router.route('/record/battle').post(wrapAsync(authMiddleware), fileUploader, uploadS3, writeBattleFile);

module.exports = router;
