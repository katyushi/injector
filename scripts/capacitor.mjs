import os from 'node:os';
import { createRequire } from 'node:module';

const nativeUserInfo = os.userInfo.bind(os);
os.userInfo = () => {
  try {
    return nativeUserInfo();
  } catch {
    return {
      username: process.env.USERNAME || 'user',
      homedir: process.env.USERPROFILE || process.cwd(),
      shell: process.env.SHELL || process.env.COMSPEC || 'cmd.exe',
    };
  }
};

const require = createRequire(import.meta.url);
const cliPath = require.resolve('@capacitor/cli/bin/capacitor');
process.argv = [process.execPath, cliPath, ...process.argv.slice(2)];
require(cliPath);
