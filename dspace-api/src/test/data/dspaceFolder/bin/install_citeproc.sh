#!/bin/sh
git clone --recurse-submodules https://github.com/zotero/citeproc-js-server.git
cd citeproc-js-server
npm install
cp ./dspace/config/crosswalks/csl/* csl/
nohup npm start &
cd -
