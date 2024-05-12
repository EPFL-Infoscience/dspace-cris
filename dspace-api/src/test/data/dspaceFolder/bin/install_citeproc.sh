#!/bin/sh
# installs NVM (Node Version Manager)
cd ..
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
# enable npm in the current terminal
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
# download and install Node.js
nvm install 16
# verifies the right Node.js version is in the environment
node -v
# verifies the right NPM version is in the environment
npm -v
git clone --recurse-submodules https://github.com/zotero/citeproc-js-server.git
cd citeproc-js-server
npm install
cp ../dspace/config/crosswalks/csl/* csl/
nohup npm start &
cd -
