const exec = require("cordova/exec");
const PLUGIN_NAME = "ZipLoader";

module.exports = {
    downloadZip: function(url) {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, PLUGIN_NAME, "downloadZip", [url]);
        });
    }
};
