const { S3 } = require('aws-sdk');
require('dotenv').config();

const preparePrefix = (prefixList) => {
  let folderPrefix = '';
  prefixList.forEach((prefix) => {
    folderPrefix += `${prefix}/`;
  });
  return folderPrefix;
};

const uploadS3 = async (req, res, next) => {
  const { file, files } = req;
  const {
    projectID, versionID, reqCategory,
  } = req.body;
  const { user } = req;

  let targetFiles = [];
  if (file) {
    targetFiles.push(file);
  } else if (files) {
    targetFiles = files;
  }
  const logNumber = Date.now();
  req.s3Results = [];
  req.filenames = [];
  req.log = logNumber;
  await Promise.all(targetFiles.map(async (tmpFile) => {
    let folderRoutes;
    switch (reqCategory) {
      case 'code_file':
        folderRoutes = preparePrefix([process.env.S3_RECORD_FOLDER, `user_${user.id}`, `project_${projectID}`, `version_${versionID}`]);
        folderRoutes += `${logNumber}-`;
        break;
      case 'user_picture':
        folderRoutes = preparePrefix([process.env.S3_USER_IMAGE_FOLDER, `user_${user.id}`]);
        break;
      default:
        return res.status(400).send({ msg: 'Bad request, please provide request catecory.' });
    }
    const s3 = new S3();
    const param = {
      Bucket: process.env.AWS_BUCKET_NAME,
      Key: `${folderRoutes}${tmpFile.originalname}`,
      Body: tmpFile.buffer,
    };
    req.filenames.push(tmpFile.originalname);
    req.s3Results.push(await s3.upload(param).promise());
  }));
  return next();
};

module.exports = uploadS3;
