var exec = require('cordova/exec');

exports.print = function(mac, str, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'ZebraPrinterPlugin', 'print', [mac, str]);
};

exports.printWithImg = function(mac, str, filePath, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'ZebraPrinterPlugin', 'printWithImg', [mac, str, filePath]);
};

exports.find = function(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'ZebraPrinterPlugin', 'find', []);
};

exports.usbPrint = function(zpl, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'ZebraPrinterPlugin', 'usbPrint', [zpl]);
};

exports.usbFind = function(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, 'ZebraPrinterPlugin', 'usbFind', []);
};

