const getBattleMarketing = (req, res) => {
  console.log('getting marketing');
  return res.status(200).json({ data: 'marketing' });
};

module.exports = {
  getBattleMarketing,
};
