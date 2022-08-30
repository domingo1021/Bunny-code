const router = require('express').Router();
const { runCompiler } = require('../controllers/compiler');

router.get('/compiler', runCompiler);

module.exports = router;
