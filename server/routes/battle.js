const express = require('express');
const { createBattle, getAllBattles, ifBattleExists } = require('../controllers/battle');

const router = express.Router();

router.route('/battle').get(getAllBattles).post(createBattle);

router.get('/battle/:battleName', ifBattleExists);

module.exports = router;
