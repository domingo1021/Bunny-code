const {NodeVM} = require('vm2');
const fs = require('fs');
const file = process.env.CODE_FILE || "test.js";

const vm = new NodeVM({
  console: 'inherit',
  //sandbox: {},
  //require: {
     // external: true,
      //builtin: ['fs', 'path'],
      //root: './',
      //mock: {
          //fs: {
              //readFileSync: () => 'Nice try!'
          //}
      //}
  //}
});

vm.run(fs.readFileSync(file).toString());
