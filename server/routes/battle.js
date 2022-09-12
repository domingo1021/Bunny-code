const express = require('express');
const { createBattle } = require('../controllers/battle');

const router = express.Router();

router.post('/battle', createBattle);

module.exports = router;
