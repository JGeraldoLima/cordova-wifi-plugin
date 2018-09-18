import {Component} from '@angular/core';
import {NavController, Platform, AlertController} from 'ionic-angular';
import {AndroidPermissions} from "@ionic-native/android-permissions";

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {

  availableNetworks = [];

  selectedNetwork;

  password;

  private permissionGranted: boolean = false;

  private avoidReconnectionIfSuccess: boolean = true;

  private skipEmptySSIDs: boolean = true;

  private GET_NEW_NETWORKS_LIST_INTERVAL: number = 2000;

  private wifiManagementPermissions: string[] = [
    this.androidPermissions.PERMISSION.ACCESS_FINE_LOCATION,
    this.androidPermissions.PERMISSION.ACCESS_WIFI_STATE,
    this.androidPermissions.PERMISSION.CHANGE_WIFI_STATE
  ];

  constructor(public platform: Platform, private androidPermissions: AndroidPermissions,
              public alertCtrl: AlertController) {
  }

  ionViewDidLoad() {
    if (this.platform.is('android')) {
      this.checkPermissions();
    } else if (this.platform.is('ios')) {
      this.getWifiNetworksList();
    } else {
      this.showAlert('Error', 'This app must run in an Android or iOS device')
    }
  }

  private checkPermissions() {
    this.wifiManagementPermissions.forEach((permissionName) => {
      this.androidPermissions.checkPermission(permissionName).then(
        (result) => {
          this.checkNeededPermissionsGrant(result.hasPermission, permissionName);
        },
        (err) => {
          console.log("permissions error 1: ", err);
        }
      );
    });
  }

  private checkNeededPermissionsGrant(hasPermission: boolean, permissionName: string) {
    if (!hasPermission) {
      this.androidPermissions
        .requestPermission(permissionName)
        .then(
          (result) => {
            if (!result.hasPermission) {
              this.permissionGranted = false;
              this.showAlert('Permission', 'You will not be able ' +
                'to manage wifi sensor until you give this permission');
            } else {
              this.permissionGranted = true;
              this.getWifiNetworksList();
            }
          },
          (err) => {
            this.checkPermissions();
          }
        );
    }

    if (permissionName === this.androidPermissions.PERMISSION.ACCESS_FINE_LOCATION
      && hasPermission) {
      this.permissionGranted = true;
      this.getWifiNetworksList();
    }
  }

  private getWifiNetworksList = () => {
    //TODO: add loading
    this.getWiFiPlugin().startWifiScan((res) => {
      this.availableNetworks = res;
    }, (err) => {
      console.log('error', err);
    }, this.GET_NEW_NETWORKS_LIST_INTERVAL, this.skipEmptySSIDs);
  };

  private connect() {
    this.getWiFiPlugin().connect((res) => {
      console.log('CONNECT RESPONSE', res);

      if (res && res.includes('connected')) {
        this.getWiFiPlugin().stopWifiScan();
        this.showAlert('Success', 'Network connected');
      }
    }, (err) => {
      console.log('error trying to connect: ', err);

      if (err) {
        let resultsMap = {
          'network_connection_error - disconnected': 'Network disconnected',
          'network_connection_error - network_not_found': 'Network not found',
          'wifi_is_disabled': 'Wifi is disabled. Please turn it on before proceed'
        };

        this.getWiFiPlugin().stopWifiScan();
        this.showAlert('Error', resultsMap[err]);
      }
    }, this.selectedNetwork.ssid, this.password, this.avoidReconnectionIfSuccess);
  }

  private getWiFiPlugin() {
    // @ts-ignore
    return window.cordova.plugins.WifiManagerPlugin;
  }

  private showAlert = (title, message) => {
    const prompt = this.alertCtrl.create({
      title: title,
      message: message,
      buttons: ['OK']
    });
    prompt.present();
  }

}
