const router = require('express').Router();
const { fileUploader } = require('../services/service');
const { jwtAuthenticate } = require('../services/auth');
const {
  runCompiler, writeRecord, queryRecord, writeFile,
} = require('../controllers/compiler');
const uploadS3 = require('../services/cloudStorage');

router.get('/compiler', runCompiler);

router.route('/record').post(writeRecord);

router.route('/record/:userID').post(queryRecord);

router.post('/record/file', jwtAuthenticate, fileUploader, uploadS3, writeFile);

module.exports = router;
