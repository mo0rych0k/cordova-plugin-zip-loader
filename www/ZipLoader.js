const exec = require("cordova/exec");
const PLUGIN_NAME = "ZipLoader";
module.exports = {
    downloadZip: function(url, resolve, reject) {
        return exec(resolve, reject, PLUGIN_NAME, "downloadZip", [url]);
    },

    remove: function(path, resolve, reject) {
        return exec(resolve, reject, PLUGIN_NAME, "remove", [path]);
    }
};
