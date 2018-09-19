let WifiManagerPlugin = function (require, exports, module) {
  let exec = require('cordova/exec');

  const PLUGIN_NAME = 'CordovaWifiManager';

  const START_SCAN_CMD = 'startWifiScan'; //only for Android
  const GET_CURRENT_SCAN_LIST_CMD = 'getAvailableNetworksList'; // for now, only for Android
  const STOP_SCAN_CMD = 'stopWifiScan'; //only for Android
  const CONNECT_CMD = 'connect';
  const CHECK_CONNECTION_STATUS_CMD = 'checkConnection';
  const DISCONNECT_CMD = 'disconnect';
  const GET_CURRENT_SSID_CMD = 'getCurrentSSID';

  let getWifiConnectionStatusIntervalId;

  function WifiManagerPlugin() {
  }

  WifiManagerPlugin.prototype.startWifiScan = function (success, failure) {
    exec(success, failure, PLUGIN_NAME, START_SCAN_CMD, []);
  };

  WifiManagerPlugin.prototype.getAvailableNetworksList = function (success, failure, skipEmptySSIDs) {
    exec(success, failure, PLUGIN_NAME, GET_CURRENT_SCAN_LIST_CMD, [{"skipEmptySSIDs": skipEmptySSIDs}]);
  };

  WifiManagerPlugin.prototype.stopWifiScan = function (success, failure) {
    if (getWifiConnectionStatusIntervalId) {
      clearInterval(getWifiConnectionStatusIntervalId);
      getWifiConnectionStatusIntervalId = null;
    }
    exec(success, failure, PLUGIN_NAME, STOP_SCAN_CMD, []);
  };

  WifiManagerPlugin.prototype.connect = function (success, failure, ssid, password, avoidReconnectionIfSuccess) {
    exec(success, failure, PLUGIN_NAME, CONNECT_CMD, [{
      "ssid": ssid,
      "password": password,
      "avoidReconnectionIfSuccess": avoidReconnectionIfSuccess
    }]);

    getWifiConnectionStatusIntervalId = setInterval(function () {
      exec(success, failure, PLUGIN_NAME, CHECK_CONNECTION_STATUS_CMD, [{"ssid": ssid}]);
    }, 1500);
  };

  WifiManagerPlugin.prototype.disconnect = function (success, failure) {
    if (getWifiConnectionStatusIntervalId) {
      clearInterval(getWifiConnectionStatusIntervalId);
      getWifiConnectionStatusIntervalId = null;
    }

    exec(success, failure, PLUGIN_NAME, DISCONNECT_CMD, []);
  };

  WifiManagerPlugin.prototype.getCurrentSSID = function (success, failure) {
    exec(success, failure, PLUGIN_NAME, GET_CURRENT_SSID_CMD, []);
  };

  let wifiScan = new WifiManagerPlugin();
  module.exports = wifiScan;
};

WifiManagerPlugin(require, exports, module);

cordova.define("cordova/plugin/WifiManagerPlugin", WifiManagerPlugin);
