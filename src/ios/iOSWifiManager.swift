#if !arch(i386) && !arch(x86_64)
import NetworkExtension
#endif

import Foundation
import SystemConfiguration.CaptiveNetwork

@objc(iOSWifiManager) class iOSWifiManager : CDVPlugin {

    func connect(_ command: CDVInvokedUrlCommand) {

        let args: NSDictionary = command.arguments[0] as! NSDictionary;

        let ssid: String = args.object(forKey: "ssid") as! String
        let password: String = args.object(forKey: "password") as! String

        #if !arch(i386) && !arch(x86_64)
        if #available(iOS 11.0, *) {
            let WiFiConfig = NEHotspotConfiguration(ssid: ssid, passphrase: password, isWEP: false)
            WiFiConfig.joinOnce = false

            NEHotspotConfigurationManager.shared.apply(WiFiConfig) { error in
                if let error = error as NSError? {
                    // Failure
                    print(error.localizedDescription)
                }

                let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: self.getWifi())

                self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
            }
        } else {
            // Fallback on earlier versions
        }
        #endif
    }

    func disconnect(_ command: CDVInvokedUrlCommand) {

        let args: NSDictionary = command.arguments[0] as! NSDictionary;

        let ssid: String = args.object(forKey: "ssid") as! String

        #if !arch(i386) && !arch(x86_64)
        if #available(iOS 11.0, *) {

            NEHotspotConfigurationManager.shared.removeConfiguration(forSSID: ssid)
        } else {
            // Fallback on earlier versions
        }
        #endif
    }

    func getCurrentSSID(_ command: CDVInvokedUrlCommand) {
        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: self.getWifi())

        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }

    func checkConnection(_ command: CDVInvokedUrlCommand) {
        let args: NSDictionary = command.arguments[0] as! NSDictionary;

        let ssid: String = args.object(forKey: "ssid") as! String

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: (self.getWifi() == ssid))

        self.commandDelegate!.send(pluginResult, callbackId: command.callbackId)
    }

    func getWifi() -> String? {
        var ssid: String?
        if let interfaces = CNCopySupportedInterfaces() as NSArray? {
            for interface in interfaces {
                if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
                    ssid = interfaceInfo[kCNNetworkInfoKeySSID as String] as? String
                    break
                }
            }
        }

        return ssid
    }
}
