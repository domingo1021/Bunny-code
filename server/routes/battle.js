const express = require('express');
const { createBattle, getAllBattles } = require('../controllers/battle');

const router = express.Router();

router.route('/battle').get(getAllBattles).post(createBattle);

module.exports = router;
