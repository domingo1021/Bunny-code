require('dotenv').config();
const validator = require('express-validator');
const Project = require('../models/project');
const Exception = require('../services/execption');

const searchProjects = async (keywords, paging) => {
  const responseObject = await Project.searchProjects(keywords, paging);
  return responseObject;
};

const projectDetails = async (projectName) => {
  const detailResults = await Project.projectDetials(projectName);
  if (detailResults === -1) {
    throw new Exception('Bad request', 400);
  }
  const [projectData, versionData, fileData, recordData] = detailResults;
  const versionCompisition = versionData.map((version) => {
    if (!version.files) {
      version.files = [];
    }
    if (!version.records) {
      version.records = [];
    }
    fileData.forEach((file) => {
      if (file.length !== 0) {
        if (version.versionID === file[0].versionID) {
          file[0].fileURL = process.env.AWS_DISTRIBUTION_NAME + file[0].fileURL;
          version.files.push(file[0]);
        }
      }
    });
    recordData.forEach((record) => {
      if (record.length !== 0) {
        if (version.versionID === record[0].versionID) {
          version.records.push(record[0]);
        }
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
        responseObject = await getAllProjects();
      } else {
        responseObject = await searchProjects(keywords, +paging);
      }
      break;
    case 'detail':
      if (!projectName) {
        return res.status(400).json({ msg: 'Bad request, please provide proejct id.' });
      }
      try {
        responseObject = await projectDetails(projectName);
      } catch (error) {
        return res.status(error.status).json({ msg: error.msg });
      }
      break;
    case 'top':
      responseObject = await getTopThreeProjects();
      break;
    default:
      // all
      responseObject = await getAllProjects(+paging);
      break;
  }
  // await Project.getAllProjects();
  return res.status(200).json({
    data: responseObject,
  });
};

const createProjectVersion = async (req, res) => {
  const { projectID } = req.params;
  const { versionName, fileName } = req.body;
  const errors = validator.validationResult(req);
  if (!errors.isEmpty()) {
    const errorMessage = errors.array()[0].msg;
    return res.status(400).json({ msg: errorMessage });
  }
  if (!projectID || !versionName || !fileName) {
    return res.status(400).json({ msg: 'Lake of data' });
  }
  const responseObject = await Project.createProjectVersion(versionName, fileName, +projectID);
  if (responseObject.msg !== undefined) {
    console.log('return 400.');
    return res.status(responseObject.status).json({ msg: responseObject.msg });
  }
  return res.status(201).json({ data: { ...responseObject } });
};

const updateProject = async (req, res) => {
  // TODO: update user status or add new record when user click star.
  const { information } = req.params;
  const { projectID } = req.query;
  if (!projectID) {
    return res.status(400).json({ msg: 'Lake of data: projectID' });
  }
  switch (information) {
    case 'watch':
      try {
        console.log('put call project', +projectID);
        await Project.updateWatchCount(+projectID);
      } catch (error) {
        console.log('update watch count exception: ', error);
        return res.status(500).json({ msg: 'Internal server error' });
      }
      break;
    case 'star':
      try {
        await Project.updateStarCount(+projectID);
      } catch (error) {
        console.log('update star count exception: ', error);
        return res.status(500).json({ msg: 'Internal server error' });
      }
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
