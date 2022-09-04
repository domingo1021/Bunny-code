const multer = require('multer');

class FileUploadException {
  constructor(msg) {
    this.msg = msg;
  }
}

const fileUploader = multer({
  storage: multer.memoryStorage(),
  fileFilter: (req, file, cb) => {
    const fileSize = parseInt(req.headers['content-length']);
    if (file.mimetype !== 'application/javascript') {
      cb(new FileUploadException('Only javascript file is accepted'));
    } else if (fileSize >= 1024 * 1024 * 3) {
      cb(new FileUploadException('File too large.'));
    } else {
      cb(null, true);
    }
  },
}).array('files', 5);

function wrapAsync(fn) {
  return (req, res, next) => {
    fn(req, res, next).catch(next);
  };
}

module.exports = { wrapAsync, fileUploader, FileUploadException };
