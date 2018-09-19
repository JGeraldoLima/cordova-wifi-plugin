import {Component} from '@angular/core';
import {
  NavController,
  Platform,
  AlertController,
  LoadingController,
  ToastController
} from 'ionic-angular';
import {AndroidPermissions} from "@ionic-native/android-permissions";

@Component({
  selector: 'page-home',
  templateUrl: 'home.html'
})
export class HomePage {

  scanStarted: boolean = false;

  connectedToAccessPoint: boolean = false;

  availableNetworks = [];

  selectedNetwork;

  password;

  private permissionGranted: boolean = false;

  private avoidReconnectionIfSuccess: boolean = true;

  private skipEmptySSIDs: boolean = true;

  private loading;

  private wifiManagementPermissions: string[] = [
    this.androidPermissions.PERMISSION.ACCESS_FINE_LOCATION,
    this.androidPermissions.PERMISSION.ACCESS_WIFI_STATE,
    this.androidPermissions.PERMISSION.CHANGE_WIFI_STATE
  ];

  constructor(public platform: Platform, private androidPermissions: AndroidPermissions,
              private loadingCtrl: LoadingController, public alertCtrl: AlertController,
              private toastCtrl: ToastController) {
  }

  ionViewDidLoad() {
    if (this.platform.is('android')) {
      this.checkPermissions();
    } else if (this.platform.is('ios')) {
      this.scanStarted = true;
      this.getNetworksList();
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
    }
  }

  getCurrentSSID = () => {
    this.showLoading('Loading available wifi access points...');
    this.getWiFiPlugin().getCurrentSSID((res) => {
      this.dismissLoading();
      this.presentToast(`Current SSID: ${res || 'none'}`);
    }, (err) => {
      console.log('error', err);
      this.dismissLoading();
    });
  };

  //only needed for Android
  startScan = () => {
    this.getWiFiPlugin().startWifiScan(
      () => {
        this.scanStarted = true;
        this.presentToast('scan started');
      }, (err) => {
        this.presentToast(`error: ${err}`);
      });
  };

  //only needed for Android
  stopWifiScan = () => {
    this.getWiFiPlugin().startWifiScan(
      () => {
        this.scanStarted = false;
        this.presentToast('scan stopped');
      }, (err) => {
        this.presentToast(`error: ${err}`);
      });
  };

  getNetworksList = () => {
    this.showLoading('Loading available wifi access points...');
    this.getWiFiPlugin().getAvailableNetworksList((res) => {
      this.availableNetworks = res;
      this.dismissLoading();
    }, (err) => {
      console.log('error', err);
      this.dismissLoading();
    }, this.skipEmptySSIDs);
  };

  connect() {
    this.showLoading(`Connecting to ${this.selectedNetwork.SSID}`);
    this.getWiFiPlugin().connect((res) => {
      console.log('CONNECT RESPONSE', res);

      if (res && res.includes('connected')) {
        this.connectedToAccessPoint = true;
        this.getWiFiPlugin().stopWifiScan();
        this.dismissLoading();
        this.showAlert('Success', 'Network connected');
      }
    }, (err) => {
      console.log('error trying to connect: ', err);
      this.dismissLoading();

      if (err) {
        let resultsMap = {
          'network_connection_error - disconnected': 'Network disconnected',
          'network_connection_error - network_not_found': 'Network not found',
          'wifi_is_disabled': 'Wifi is disabled. Please turn it on before proceed'
        };

        this.getWiFiPlugin().stopWifiScan();
        this.showAlert('Error', resultsMap[err]);
      }
    }, this.selectedNetwork.SSID, this.password, this.avoidReconnectionIfSuccess);
  }

  disconnect = () => {
    this.showLoading('Disconnecting...');
    this.getWiFiPlugin().disconnect((res) => {
      this.connectedToAccessPoint = false;
      this.dismissLoading();
    }, (err) => {
      console.log('error', err);
      this.dismissLoading();
    });
  };

  private getWiFiPlugin() {
    // @ts-ignore
    return window.cordova.plugins.WifiManagerPlugin;
  }

  private showLoading(msg) {
    this.loading = this.loadingCtrl.create({
      content: msg
    });
    this.loading.present();
  }

  private dismissLoading() {
    this.loading.dismiss();
  }

  presentToast(msg) {
    let toast = this.toastCtrl.create({
      message: msg,
      duration: 3000,
      position: 'bottom'
    });

    toast.present();
  }

  private showAlert = (title, message) => {
    const alert = this.alertCtrl.create({
      title: title,
      message: message,
      buttons: ['OK']
    });
    alert.present();
  }

}
