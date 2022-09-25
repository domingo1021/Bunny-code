const express = require('express');
const { createBattle, getBattles, ifBattleExists } = require('../controllers/battle');

const router = express.Router();

router.route('/battle').get(getBattles).post(createBattle);

router.get('/battle/:battleName', ifBattleExists);

module.exports = router;
