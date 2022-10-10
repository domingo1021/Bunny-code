const router = require('express').Router();
const { param } = require('express-validator');
const { validateNormalName, validateFilter } = require('../services/validation');
const { getBattles, ifBattleExists } = require('../controllers/battle');

router.route('/battle').get(getBattles);

router.get(
  '/battle/:battleName',
  [
    param('battleName').custom((battleName) => {
      if (!validateNormalName(battleName)) {
        throw new Error('Battle name should only include number, alphabet, dot or _ .');
      } return true;
    }),
  ],
  validateFilter,
  ifBattleExists,
);

module.exports = router;
