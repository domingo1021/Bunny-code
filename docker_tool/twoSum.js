// require('dotenv').config()
const { twoSum } = require(process.env.TWOSUMFILE);

console.log(process.env.TWOSUMFILE);
console.log(twoSum);

const compilerResults = [];
compilerResults.push(twoSum([2, 7, 11, 15], 9));
compilerResults.push(twoSum([2, 7, 11, 15], 26));
compilerResults.push(twoSum([1, 1, 1, 1], 2));
compilerResults.push(twoSum([-1], 10));

console.log(compilerResults);