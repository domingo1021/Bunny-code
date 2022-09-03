const router = require('express').Router();
const { runCompiler, writeRecord, queryRecord } = require('../controllers/compiler');

router.get('/compiler', runCompiler);

router.route('/record').get(queryRecord).post(writeRecord);

module.exports = router;
