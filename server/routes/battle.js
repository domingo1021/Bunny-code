const express = require('express');
const { getBattles, ifBattleExists } = require('../controllers/battle');

const router = express.Router();

router.route('/battle').get(getBattles);

router.get('/battle/:battleName', ifBattleExists);

module.exports = router;
