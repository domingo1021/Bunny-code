const CLIENT_CATEGORY = {
  visitor: 0,
  otherMember: 1,
  self: 2,
};

const authorization = (userID, userPayload) => {
  if (!userPayload) {
    return CLIENT_CATEGORY.visitor;
  }
  if (userID !== userPayload) {
    return CLIENT_CATEGORY.otherMember;
  }
  return CLIENT_CATEGORY.self;
};

module.exports = {
  CLIENT_CATEGORY,
  authorization,
};
