const Project = require('../models/project');

const getProjects = async (req, res) => {
  await Project.getAllProjects();
  return res.status(200).json({
    data: 'get user data.',
  });
};

const getProejctVersions = async (req, res) => {
  await Project.getProejctVersions();
  console.log('getting...');
  return res.status(200).json({ data: 'get user project' });
};

const createProjectVersion = async (req, res) => {
  const { projectID } = req.params;
  const { versionName } = req.body;
  let versionNumber;
  try {
    versionNumber = await Project.createProjectVersion(versionName, projectID);
  } catch (error) {
    console.log(error);
    return res.send('something wrong');
  }
  return res.status(201).json({ data: { versionNumber } });
};

const getFiles = async (req, res) => {
  const { versionID } = req.body;
  return res.status(200).json({ msg: 123 });
};

const createFile = async (req, res) => {
  const { versionID } = req.body;
  return res.status(201).json({ msg: 123 });
};

module.exports = {
  getProjects,
  getProejctVersions,
  createProjectVersion,
  getFiles,
  createFile,
};
