const express = require('express');
const { param } = require('express-validator');
const { validateNormalName } = require('../services/validation');
const { getBattles, ifBattleExists } = require('../controllers/battle');

const router = express.Router();

router.route('/battle').get(getBattles);

// battle name validation
router.get(
  '/battle/:battleName',
  [
    param('battleName').custom((battleName) => {
      if (!validateNormalName(battleName)) {
        throw new Error('Battle name should only include number, alphabet, dot or _ .');
      } return true;
    }),
  ],
  ifBattleExists,
);

module.exports = router;
