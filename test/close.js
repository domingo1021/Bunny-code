const { closeConnection, truncateFakeData } = require('./fake_data_generator');
const { requester } = require('./setup');

after(async () => {
  await truncateFakeData();
  await closeConnection();
  requester.close();
});
