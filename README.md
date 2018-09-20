# cordova-wifi-plugin
A Cordova plugin for managing Wifi access points and connection state on Android and iOS

========

The objective of this new plugin is to handle correctly Android async Wifi state updates, to be able to properlu detect connection and disconnection, besides better access points list filtering. Even the official Ionic Native plugin has your problems and is no longer being maintained.

iOS treats Wifi management easier, so most of times you need just a few lines of code to solve the problem.

If you need any feature or if you want to help, feel free to fork or open pull requests (especially if you are an iOS dev):)
I will set a pattern for pull requests soon, so we can keep organized.


Installation
--------

```bash
cordova plugin add cordova-wifi-plugin
```

※ Support Android SDK >= 14 (need to test on 26+ versions)

Usage
--------

-- for Android: need to request following permissions (for API 23+):
- this.androidPermissions.PERMISSION.ACCESS_FINE_LOCATION (or this.androidPermissions.PERMISSION.ACCESS_COARSE_LOCATION)
- this.androidPermissions.PERMISSION.ACCESS_WIFI_STATE
- this.androidPermissions.PERMISSION.CHANGE_WIFI_STATE

### API

```typescript
let wifiManager = window.cordova.plugins.WifiManagerPlugin;

/* - only needed for Android
Starts an wifi state change + access points list updates listener, so we are able to get updated data.
*/
wifiManager.startWifiScan(successCallback, errorCallback);

/* @skipEmptySSIDs is optional
Gets current devices access point list, if available.
*/
wifiManager.getAvailableNetworksList(successCallback, errorCallback, skipEmptySSIDs);

/* - only needed for Android
Stops wifi state listener to avoid devices resources consumption when it is no needed anymore (ex.: when you do not want access points list updates anymore).
*/
wifiManager.stopWifiScan(successCallback, errorCallback); //only needed for Android

/* @ssid Access point SSID
   @password Access point password
   @avoidReconnectionIfSuccess (optional) Flag to avoid reconnection with last connected access point, in case of request network does not have internet access (Android default behavior).
Attempt to connect to an access point. For now, only WEP networks are supported, but this will be improved to accept all possible types soon.
*/
wifiManager.connect(successCallback, errorCallback, ssid, password, avoidReconnectionIfSuccess); 

/* 
Disconnect from current connected access point.
*/
wifiManager.disconnect(successCallback, errorCallback);

/* 
Retrieve current connected access point SSID.
*/
wifiManager.getCurrentSSID(successCallback, errorCallback);
```

## Examples
Check out my sample, it is simple and direct.


# CHANGE LOG

## 1.0.4
* Removed `getNetworksList` command from inside startScan to be independent

## 1.0.3
* Added missing `skipEmptySSIDs` param on check connection status method
* Created valid sample

## 1.0.2
* Added Android permissions on plugin.xml config

## 1.0.1
* Fixed Android class path on plugin.xml

## 1.0.0
* First version

License
--------

    Copyright (C) 2018 José Geraldo de Lima Júnior and Artur Alves de Farias

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
