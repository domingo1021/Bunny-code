const Project = require('../models/project');

const getProjects = (req, res) => {
  Project.getAllProjects();
  return res.status(200).json({
    data: 'get user data.',
  });
};

module.exports = {
  getProjects,
};
