require('dotenv').config();
const Project = require('../models/project');

const searchProjects = async (keywords, paging) => {
  const responseObject = await Project.searchProjects(keywords, paging);
  return responseObject;
};

const projectDetails = async (projectName) => {
  const detailResults = await Project.projectDetails(projectName);
  const [projectData, versionData, fileData, recordData] = detailResults;
  const versionCompisition = versionData.map((version) => {
    if (!version.files) {
      version.files = [];
    }
    if (!version.records) {
      version.records = [];
    }
    // compose file for version.
    fileData.forEach((file) => {
      if (version.versionID === file.versionID) {
        file.fileURL = process.env.AWS_DISTRIBUTION_NAME + file.fileURL;
        version.files.push(file);
      }
    });
    // compose record for version.
    recordData.forEach((record) => {
      if (!record) {
        return;
      }
      if (version.versionID === record.versionID) {
        version.records.push(record);
      }
    });
    return version;
  });
  const responseObject = {
    ...projectData,
    version: [...versionCompisition],
  };
  return responseObject;
};

const getAllProjects = async (paging) => {
  const responseObject = await Project.getAllProjects(paging);
  return responseObject;
};

const getTopThreeProjects = async () => {
  const responseObject = await Project.getTopThreeProjects();
  return responseObject;
};

const getProjects = async (req, res) => {
  const { information } = req.params;
  const { projectName, keywords } = req.query;
  const paging = req.query.paging || 0;
  let responseObject;
  switch (information) {
    case 'search':
      if (!keywords) {
        responseObject = await getAllProjects(+paging);
      } else {
        responseObject = await searchProjects(keywords, +paging);
      }
      break;
    case 'detail':
      if (!projectName) {
        return res.status(400).json({ msg: 'Bad request, please provide proejct id.' });
      }
      responseObject = await projectDetails(projectName);
      break;
    case 'top':
      responseObject = await getTopThreeProjects();
      break;
    default:
      // all
      responseObject = await getAllProjects(+paging);
      break;
  }
  return res.status(200).json({
    data: responseObject,
  });
};

const createProjectVersion = async (req, res) => {
  const { projectID } = req.params;
  const { versionName, fileName } = req.body;

  if (!projectID || !versionName || !fileName) return res.status(400).json({ msg: 'Lake of data' });

  const responseObject = await Project.createProjectVersion(versionName, fileName, +projectID);

  return res.status(201).json({ data: { ...responseObject } });
};

const updateProject = async (req, res) => {
  const { information } = req.params;
  const { projectID } = req.query;
  if (!projectID) {
    return res.status(400).json({ msg: 'Lake of data: projectID' });
  }
  switch (information) {
    case 'watch':
      await Project.updateWatchCount(+projectID);
      break;
    case 'star':
      await Project.updateStarCount(+projectID);
      break;
    default:
      return res.status(400).json({ msg: 'Bad request' });
  }
  return res.status(204).json({ data: 'No content' });
};

module.exports = {
  getProjects,
  createProjectVersion,
  updateProject,
};
