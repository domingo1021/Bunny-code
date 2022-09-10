const editVersion = async () => {
  console.log('change edit status');
  return { data: 'status change' };
};

const versionEditStatus = async () => {
  console.log('check status');
  return { data: 'status' };
};

module.exports = {
  editVersion,
  versionEditStatus,
};
