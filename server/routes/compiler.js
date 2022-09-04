const router = require('express').Router();
const { fileUploader } = require('../services/service');
const { jwtAuthenticate } = require('../services/auth');
const { runCompiler, writeRecord, queryRecord } = require('../controllers/compiler');
const uploadS3 = require('../services/cloudStorage');

router.get('/compiler', runCompiler);

router.route('/record').get(queryRecord).post(writeRecord);

router.post('/record/file', jwtAuthenticate, fileUploader, uploadS3, (req, res) => res.send('hello world !'));

module.exports = router;
