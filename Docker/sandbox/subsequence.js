const { getLCS } = require(`${process.env.LCS_FILE}`);

const compilerResults = [];
compilerResults.push(getLCS('abcde', 'ace'));
compilerResults.push(getLCS('ilovetaiwan', 'taiwan'));
compilerResults.push(getLCS('helloworld', 'playword'));
compilerResults.push(getLCS('abcdgh', 'aedfhr'));
compilerResults.push(getLCS('abcdef', 'ijklmn'));

console.log(compilerResults);
