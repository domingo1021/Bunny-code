const Project = require('../models/project');

const searchProjects = async (keywords, paging) => {
  const responseObject = await Project.searchProjects(keywords, paging);
  return responseObject;
};

const projectDetails = async (projectID) => {
  const [projectData, versionData, fileData, recordData] = await Project.projectDetials(projectID);
  versionData.map((version) => {
    if (!version.files) {
      version.files = [];
    }
    if (!version.records) {
      version.records = [];
    }
    fileData.forEach((file) => {
      if (file.length !== 0) {
        if (version.versionID === file[0].versionID) {
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
    version: [...versionData],
  };
  return responseObject;
};

const getAllProjects = async (paging) => {
  const responseObject = await Project.getAllProjects(paging);
  return responseObject;
};

const getProjects = async (req, res) => {
  const { information } = req.params;
  const { id, keywords } = req.query;
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
      if (!id) {
        return res.stauts(400).json({ msg: 'Bad request, please provide proejct id.' });
      }
      responseObject = await projectDetails(+id);
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

const getProejctVersions = async (req, res) => {
  await Project.getProejctVersions();
  console.log('getting...');
  return res.status(200).json({ data: 'get user project' });
};

const createProjectVersion = async (req, res) => {
  const { projectID } = req.params;
  const { versionName } = req.body;
  const versionID = await Project.createProjectVersion(versionName, projectID);
  if (versionID === -1) {
    return res.status(400).json({ message: 'Invalid data.' });
  }
  return res.status(201).json({ data: { versionID } });
};

module.exports = {
  getProjects,
  getProejctVersions,
  createProjectVersion,
};
