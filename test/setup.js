/* eslint-disable no-undef */
const chai = require('chai');
const deepEqualInAnyOrder = require('deep-equal-in-any-order');
const chaiHttp = require('chai-http');
const { app } = require('../app');

const { NODE_ENV } = process.env;
const { truncateFakeData, createFakeUser } = require('./fake_data_generator');
const { users } = require('./fake_data')(5);

// integration test.
chai.use(chaiHttp);
chai.use(deepEqualInAnyOrder);

const { assert, expect } = chai;
const requester = chai.request(app).keepOpen();

before(async () => {
  if (NODE_ENV !== 'test') {
    throw new Error('Not in test env');
  }
  // truncate data before start
  await truncateFakeData();
  await createFakeUser(users);
});

module.exports = {
  expect,
  assert,
  requester,
};
