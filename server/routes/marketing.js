const express = require('express');
const { getBattleMarketing } = require('../controllers/marketing');

const router = express.Router();

router.get('/marketing/battle', getBattleMarketing);

module.exports = router;
