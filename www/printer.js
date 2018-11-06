var BrotherPrinter = function () {}
BrotherPrinter.prototype = {
    findNetworkPrinters: function (callback, scope) {
        var callbackFn = function () {
            callback.apply(scope || window, arguments)
        }
        cordova.exec(callbackFn, null, 'BrotherPrinter', 'findNetworkPrinters', [])
    },

    findBluetoothPairedPrinters: function (callback, scope) {
        var callbackFn = function () {
            callback.apply(scope || window, arguments)
        }
        cordova.exec(callbackFn, null, 'BrotherPrinter', 'findBluetoothPairedPrinters', [])
    },

    printViaSDK: function (data, numberOfCopies, macAddress, callback) {
        if (!data || !data.length) {
            console.log('No data passed in. Expects a bitmap.')
            return
        }
        if (!macAddress || !macAddress.length) {
            console.log('No macAddress passed in. Expects a macAddress.')
            return
        }
        cordova.exec(callback, function (err) {
            console.log('error: ' + err)
        }, 'BrotherPrinter', 'printViaSDK', [data, numberOfCopies, macAddress])
    },
    printViaWifiInfra: function (data, numberOfCopies, ipAddress, macAddress, callback) {
        if (!data || !data.length) {
            console.log('No data passed in. Expects a bitmap.')
            return
        }
        if (!ipAddress || !ipAddress.length) {
            console.log('No ipAddress passed in. Expects an ipAddress.')
            return
        }
        if (!macAddress || !macAddress.length) {
            console.log('No macAddress passed in. Expects a macAddress.')
            return
        }
        cordova.exec(callback, function (err) {
            console.log('error: ' + err)
        }, 'BrotherPrinter', 'printViaWifiInfra', [data, numberOfCopies, ipAddress, macAddress])
    },
    printViaWifiInfraText: function (data, numberOfCopies, ipAddress, macAddress, modelName, labelNameIndex, callback) {
        cordova.exec(callback, function (err) {
            console.log('error: ' + err)
        }, 'BrotherPrinter', 'printViaWifiInfraText', [data, numberOfCopies, ipAddress, macAddress, modelName, labelNameIndex, modelName])
    }
}
var plugin = new BrotherPrinter()
module.exports = plugin