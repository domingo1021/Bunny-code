/* eslint-disable no-undef */
const { assert } = require('chai');
const { expect, requester } = require('./setup');
const { validateNormalName } = require('../server/services/validation');
const { APIException } = require('../server/services/exceptions/api_exception');
const { jwtAuthenticate, createJWTtoken } = require('../server/services/auth');
const { users } = require('./fake_data')(5);

function randomNumber(maxNum) {
  return Math.floor(Math.random() * maxNum);
}

// validate custom express-validator: validationNormalName.
describe('Validate if input name is verified.', () => {
  it('input normal name, return true.', () => {
    const inputName = 'domingo_test';
    assert.isTrue(validateNormalName(inputName));
  });

  it('input name too long, return false.', () => {
    const inputName = '122342617984194100rf0r80ef0g9eu018hd01d0481301cec13ce1';
    assert.isFalse(validateNormalName(inputName));
  });

  it('input name is empty, return false.', () => {
    const inputName = '';
    assert.isFalse(validateNormalName(inputName));
  });

  it('input name with invalid special characters.', () => {
    const inputName = 'doming^^o==:)';
    assert.isFalse(validateNormalName(inputName));
  });
});

// validate jwtAuthentication for jwt token.
describe('Authenticate user\'s jwt token, return user info object if validated.', () => {
  it('input validated jwt', async () => {
    const userInfo = {
      id: 100,
      name: 'test_user',
      email: 'test_100@gamil.com',
    };
    const exp = 36000;
    const jwtToken = await createJWTtoken(userInfo, exp);
    let authResult;
    try {
      authResult = await jwtAuthenticate(`Bearer ${jwtToken}`);
    } catch (error) {
      authResult = error;
    }
    assert.deepEqual(authResult, userInfo);
  });

  it('input empty token', async () => {
    const jwtToken = '';
    try {
      authResult = await jwtAuthenticate(jwtToken);
    } catch (error) {
      authResult = error;
    }
    assert.isTrue(authResult instanceof APIException);
  });

  it('input only Bearer', async () => {
    const jwtToken = '';
    try {
      authResult = await jwtAuthenticate(`Bearer ${jwtToken}`);
    } catch (error) {
      authResult = error;
    }
    assert.isTrue(authResult instanceof APIException);
  });

  it('input invalid jwt content', async () => {
    const jwtToken = '13482984713rfibfibeocb1c';
    try {
      authResult = await jwtAuthenticate(`Bearer ${jwtToken}`);
    } catch (error) {
      authResult = error;
    }
    assert.isTrue(authResult instanceof APIException);
  });
});

// integration test create user project.
describe('Check create user project api', async () => {
  it('Create user project with correct input', async () => {
    // prepare mock data.
    const userIndex = randomNumber(3);
    const jwtToken = await createJWTtoken(users[userIndex], 36000);

    // send request with chai
    const res = await requester.post(`/api/1.0/user/${userIndex + 1}/project`).set('Authorization', `Bearer ${jwtToken}`).send({
      projectName: 'Hello',
      projectDescription: 'Hello world!',
      isPublic: 1,
      versionName: 'main',
      fileName: 'test.js',
    });

    // expect first project create success (remember to truncate db first).
    expect(res.statusCode).to.equal(201);
    expect(res.body.data).to.be.key('projectID');
  });

  it('Project name already exists.', async () => {
    // prepare mock data.
    const userIndex = randomNumber(3);
    const jwtToken = await createJWTtoken(users[userIndex], 36000);

    // send request with chai
    const res = await requester.post(`/api/1.0/user/${userIndex + 1}/project`).set('Authorization', `Bearer ${jwtToken}`).send({
      projectName: 'Hello',
      projectDescription: 'Hello world!',
      isPublic: 1,
      versionName: 'main',
      fileName: 'test.js',
    });

    // expect first project create success (remember to truncate db first).
    expect(res.statusCode).to.equal(400);
    expect(res.body.msg).to.equal('project name already exists');
  });

  it('With another user\'s jwt token (should unauthorized).', async () => {
    const userIndex = randomNumber(3);
    const anotherUserIndex = (userIndex + 1) % 3;
    const jwtToken = await createJWTtoken(users[anotherUserIndex], 36000);

    const res = await requester.post(`/api/1.0/user/${userIndex + 1}/project`).set('Authorization', `Bearer ${jwtToken}`).send({
      projectName: 'Hello',
      projectDescription: 'Hello world!',
      isPublic: 1,
      versionName: 'main',
      fileName: 'test.js',
    });

    // expect first project create success (remember to truncate db first).
    expect(res.statusCode).to.equal(403);
    expect(res.body.msg).to.equal('Forbidden');
  });

  it('With invalid jwt token', async () => {
    const userIndex = randomNumber(3);
    const jwtToken = '1ce3n1oueo12cuie12oeci';

    const res = await requester.post(`/api/1.0/user/${userIndex + 1}/project`).set('Authorization', `Bearer ${jwtToken}`).send({
      projectName: 'Hello',
      projectDescription: 'Hello world!',
      isPublic: 1,
      versionName: 'main',
      fileName: 'test.js',
    });

    // expect first project create success (remember to truncate db first).
    expect(res.statusCode).to.equal(401);
    expect(res.body).deep.equal({ msg: 'Authentication failed' });
  });

  it('With invalid project name', async () => {
    const userIndex = randomNumber(3);
    const jwtToken = await createJWTtoken(users[userIndex], 36000);

    const res = await requester.post(`/api/1.0/user/${userIndex + 1}/project`).set('Authorization', `Bearer ${jwtToken}`).send({
      projectName: 'Hello&*(*&',
      projectDescription: 'Hello world!',
      isPublic: 1,
      versionName: 'main',
      fileName: 'test.js',
    });

    // expect first project create success (remember to truncate db first).
    expect(res.statusCode).to.equal(400);
    expect(res.body).deep.equal({ msg: 'Project name should only include number, alphabet, dot or _ .' });
  });
});
