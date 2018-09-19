package wifiplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import android.os.Handler;

import static android.net.ConnectivityManager.EXTRA_REASON;

public class AndroidWifiManager extends CordovaPlugin {

    /* Available response values:
     * - networks: available networks list (updates every 5 seconds)
     * - network_connection_error: when a error occurs during the connection attemp (values: 'invalid_credentials', 'network_not_found', '')
     * - conf_connection: current attemp status (values: 'connecting', 'connected', 'disconnected')
     * */

    private CallbackContext callbackContext;

    private final String START_SCAN_COMMAND = "startWifiScan";
    private final String STOP_SCAN_COMMAND = "stopWifiScan";
    private final String GET_AVAILABLE_NETWORKS_LIST_COMMAND = "getAvailableNetworksList";
    private final String CHECK_CONNECTION_ATTEMPT_STATUS_COMMAND = "checkConnection";
    private final String GET_CURRENT_SSID_COMMAND = "getCurrentSSID";
    private final String CONNECT_COMMAND = "connect";
    private final String DISCONNECT_COMMAND = "disconnect";

    private final String CONNECTION_ERROR_KEY = "network_connection_error";
    private final String CONNECTION_STATUS_KEY = "conf_connection";

    private final String WIFI_IS_DISABLED_ERROR = "wifi_is_disabled";
    private final String CONNECTING_RESPONSE_VALUE = "connecting";
    private final String CONNECTED_RESPONSE_VALUE = "connected";
    private final String DISCONNECTED_RESPONSE_VALUE = "disconnected";

    private final String ERROR_NETWORK_NOT_FOUND_VALUE = "network_not_found";
    private final String ERROR_INVALID_CREDENTIALS_VALUE = "invalid_credentials";

    private final int SCANNING_STATUS_MAX_COUNT = 6;
    private final int INACTIVE_STATUS_MAX_COUNT = 6;

    private int SCANNING_STATUS_CURRENT_COUNT = 0;
    private int INACTIVE_STATUS_CURRENT_COUNT = 0;

    //CHECK: need to be parameterized?
    private final int SCAN_FREQUENCY = 5000;

    private boolean scanEnabled = false;
    private boolean connectedToAccessPoint = false;

    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;

    private List<ScanResult> lastScanResults = new ArrayList<>();
    private String lastSSID;
    private WifiInfo lastWifiInfo;
    private SupplicantState lastSupState;
    private int lastBlockedNetworkId = -1;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        wifiManager = (WifiManager) cordova.getActivity().getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wifiScanReceiver != null) {
            cordova.getActivity().unregisterReceiver(wifiScanReceiver);
            scanEnabled = false;
        }
    }


    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        this.callbackContext = callbackContext;

        if (START_SCAN_COMMAND.equals(action)) {
            return startScan();
        } else if (STOP_SCAN_COMMAND.equals(action)) {
            stopWifiConnectReceiver();
            callbackContext.success("stop command: scan stopped");
            return true;
        } else if (GET_AVAILABLE_NETWORKS_LIST_COMMAND.equals(action)) {
            if (scanEnabled) {
                JSONObject cmdData = args.getJSONObject(0);
                return generateNetworksResponse(cmdData);
            } else {
                callbackContext.error("get scan list: scan stopped");
                return true;
            }
        } else if (CHECK_CONNECTION_ATTEMPT_STATUS_COMMAND.equals(action)) {
            if (scanEnabled) {
                return getConnectionStatus();
            } else {
                callbackContext.error("check connection status: scan stopped");
                return true;
            }
        } else if (GET_CURRENT_SSID_COMMAND.equals(action)) {
            if (wifiManager.isWifiEnabled()) {
                callbackContext.success(getCurrentSSID());
                return true;
            } else {
                callbackContext.error(WIFI_IS_DISABLED_ERROR);
                return false;
            }
        } else if (CONNECT_COMMAND.equals(action)) {
            JSONObject networkData = args.getJSONObject(0);
            return this.connectToNetwork(networkData);
        } else if (DISCONNECT_COMMAND.equals(action)) {
            return this.disconnect();
        }
        return true;
    }

    private String formatMessage(String key, String message) {
        return String.format("%s - %s", key, message);
    }

    private boolean startScan() {
        if (!wifiManager.isWifiEnabled()) {
            callbackContext.error(WIFI_IS_DISABLED_ERROR);
            return false;
        } else {
            if (wifiManager.startScan()) {
                scanEnabled = true;

                IntentFilter intentFilter = generateScanFilterIntent();

                initWifiScanReceiver();
                cordova.getActivity().registerReceiver(wifiScanReceiver, intentFilter);

                callbackContext.success("scan started");
                return true;
            } else {
                callbackContext.error("scan failed");
                return false;
            }
        }
    }

    private IntentFilter generateScanFilterIntent() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        return filter;
    }

    private void initWifiScanReceiver() {
        scanEnabled = true;
        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    lastScanResults = wifiManager.getScanResults();

                    if (scanEnabled) {
                        Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                wifiManager.startScan();
                            }
                        }, SCAN_FREQUENCY);
                    }
                }

                if (intent.getExtras() != null) {
                    lastWifiInfo = wifiManager.getConnectionInfo();
                    lastSupState = lastWifiInfo.getSupplicantState();
                }
            }
        };
    }

    private void stopWifiConnectReceiver() {
        scanEnabled = false;
        cordova.getActivity().unregisterReceiver(wifiScanReceiver);
        wifiScanReceiver = null;
    }

    private boolean getConnectionStatus() {
        if (!wifiManager.isWifiEnabled()) {
            callbackContext.error(WIFI_IS_DISABLED_ERROR);
            return false;
        } else {
            if (lastWifiInfo != null && lastSupState != null) {
                String formattedWifiSSID = String.format("\"%s\"", lastSSID);

                if (TextUtils.equals(lastWifiInfo.getSSID(), formattedWifiSSID)
                    && lastSupState == SupplicantState.COMPLETED) {
                    connectedToAccessPoint = true;
                    resetConnectionAttemptCounters();

                    callbackContext.success(formatMessage(CONNECTION_STATUS_KEY, CONNECTED_RESPONSE_VALUE));
                    return true;
                } else if (connectedToAccessPoint && !TextUtils.equals(lastWifiInfo.getSSID(), formattedWifiSSID)) {
                    connectedToAccessPoint = false;
                    resetConnectionAttemptCounters();

                    callbackContext.error(formatMessage(CONNECTION_STATUS_KEY, DISCONNECTED_RESPONSE_VALUE));
                    return false;
                } else if (!TextUtils.isEmpty(lastSSID)
                    && ((!connectedToAccessPoint && TextUtils.equals(lastWifiInfo.getSSID(), formattedWifiSSID) && lastSupState == SupplicantState.DISCONNECTED)
                    || (!TextUtils.equals(lastWifiInfo.getSSID(), formattedWifiSSID) && lastSupState == SupplicantState.COMPLETED))) {
                    resetConnectionAttemptCounters();
                    wifiManager.enableNetwork(lastBlockedNetworkId, true); //prevent keeping blocked, allowing reconnect attempt
                    callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_NETWORK_NOT_FOUND_VALUE));
                    return false;
                }

                boolean shouldKeepTrying = checkConnectionAttemptLimit();
                if (!shouldKeepTrying) {
                    wifiManager.enableNetwork(lastBlockedNetworkId, true);
                    callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_NETWORK_NOT_FOUND_VALUE));
                    return false;
                } else {
                    callbackContext.success(formatMessage(CONNECTION_STATUS_KEY, lastSupState.toString()));
                    return true;
                }
            } else {
                callbackContext.error("no data available");
                return false;
            }
        }
    }

    private boolean checkConnectionAttemptLimit() {
        String supState = lastSupState.toString();
        if (TextUtils.equals(supState, "SCANNING")) {
            SCANNING_STATUS_CURRENT_COUNT++;
        } else if (TextUtils.equals(supState, "INACTIVE")) {
            INACTIVE_STATUS_CURRENT_COUNT++;
        }

        if (SCANNING_STATUS_CURRENT_COUNT >= SCANNING_STATUS_MAX_COUNT
            || INACTIVE_STATUS_CURRENT_COUNT >= INACTIVE_STATUS_MAX_COUNT) {
            resetConnectionAttemptCounters();
            return false;
        }
        return true;
    }

    private void resetConnectionAttemptCounters() {
        SCANNING_STATUS_CURRENT_COUNT = 0;
        INACTIVE_STATUS_CURRENT_COUNT = 0;
    }

    private boolean generateNetworksResponse(JSONObject cmdData) {
        if (!wifiManager.isWifiEnabled()) {
            callbackContext.error(WIFI_IS_DISABLED_ERROR);
            return false;
        } else {

            boolean skipEmptySSIDs = false;
            try {
                skipEmptySSIDs = cmdData.getBoolean("skipEmptySSIDs");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Map<String, JSONObject> tempResults = new TreeMap<>(); //we're using a map to avoid duplicates
            for (ScanResult scan : lastScanResults) {

                if(!TextUtils.isEmpty(scan.SSID) || !skipEmptySSIDs){
                    JSONObject network = new JSONObject();
                    try {
                        network.put("SSID", scan.SSID);
                        network.put("BSSID", scan.BSSID);
                        network.put("channelFrequency", scan.frequency);
                        network.put("RSSI", scan.level);
                        network.put("capabilities", scan.capabilities);
                        tempResults.put(scan.BSSID, network);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        callbackContext.error(e.toString());
                        return false;
                    }
                }
            }
            callbackContext.success(new JSONArray(tempResults.values()));
            return true;
        }
    }

    private boolean isRequestedSSIDAvailable(String ssid) {
        List<ScanResult> currentNetworks = wifiManager.getScanResults();

        for (ScanResult scan : currentNetworks) {
            if (!TextUtils.isEmpty(scan.SSID) && scan.SSID.equals(ssid)) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentSSID() {
        WifiInfo info = wifiManager.getConnectionInfo();

        if (info == null) {
            connectedToAccessPoint = false;
            return null;
        }

        String ssid = info.getSSID();
        if (TextUtils.isEmpty(ssid) || TextUtils.equals(ssid, "<unknown ssid>")) {
            connectedToAccessPoint = false;
            return null;
        }

        return ssid;
    }

    private int findNetworkIdBySSID(String ssid) {
        List<WifiConfiguration> currentNetworks = wifiManager.getConfiguredNetworks();
        int networkId = -1;

        for (WifiConfiguration test : currentNetworks) {
            if (test.SSID != null && test.SSID.equals(ssid)) {
                networkId = test.networkId;
            }
        }
        return networkId;
    }

    private void blockLastConnectedNetwork(String ssid) {
        lastBlockedNetworkId = findNetworkIdBySSID(ssid);
        wifiManager.disableNetwork(lastBlockedNetworkId);
    }

    // TODO: give support to other networks types besides WAP
    private boolean connectToNetwork(JSONObject networkData) {
        if (!wifiManager.isWifiEnabled()) {
            callbackContext.error(WIFI_IS_DISABLED_ERROR);
            return false;
        } else {
            initWifiScanReceiver();
            cordova.getActivity().registerReceiver(wifiScanReceiver, generateScanFilterIntent());
            wifiManager.startScan();

            WifiConfiguration conf = new WifiConfiguration();

            try {
                lastSSID = networkData.getString("ssid");
                String password = networkData.getString("password");
                boolean avoidReconnectionIfSuccess = networkData.getBoolean("avoidReconnectionIfSuccess");

                if (TextUtils.isEmpty(lastSSID) || TextUtils.isEmpty(password)) {
                    scanEnabled = false;
                    callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_INVALID_CREDENTIALS_VALUE));
                    return false;
                }

                if (!isRequestedSSIDAvailable(lastSSID)) {
                    callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_NETWORK_NOT_FOUND_VALUE));
                    return false;
                }

                if (avoidReconnectionIfSuccess) {
                    String currentSSID = getCurrentSSID();

                    if (!TextUtils.isEmpty(currentSSID)) {
                        if (!TextUtils.equals(lastSSID, currentSSID)) {
                            blockLastConnectedNetwork(currentSSID);
                        } else {
                            callbackContext.error("could not block last ssid: not found");
                        }
                    }
                }

                conf.SSID = String.format("\"%s\"", lastSSID);
                conf.preSharedKey = String.format("\"%s\"", password);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            final int networkId = wifiManager.addNetwork(conf);

            final boolean[] success = {true}; //to be able to modify inside handler, suggested by IDE

            if (networkId != -1) {
                wifiManager.disconnect();

                boolean enableSuccess = wifiManager.enableNetwork(networkId, true);
                boolean reconnectSuccess = wifiManager.reconnect();

                if (!(enableSuccess && reconnectSuccess)) {
                    wifiManager.removeNetwork(networkId);
                    callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_NETWORK_NOT_FOUND_VALUE));
                    wifiManager.enableNetwork(lastBlockedNetworkId, true);
                    success[0] = false;
                }
            } else {
                wifiManager.removeNetwork(networkId);
                callbackContext.error(formatMessage(CONNECTION_ERROR_KEY, ERROR_NETWORK_NOT_FOUND_VALUE));
                wifiManager.enableNetwork(lastBlockedNetworkId, true);
                success[0] = false;
            }

            if (success[0]) {
                callbackContext.success(formatMessage(CONNECTION_STATUS_KEY, CONNECTING_RESPONSE_VALUE));
            }
            return success[0];
        }
    }

    private boolean disconnect() {
        if (!wifiManager.isWifiEnabled()) {
            callbackContext.error(WIFI_IS_DISABLED_ERROR);
            return false;
        } else {
            boolean disconnectionSuccessful;
            wifiManager.disconnect();

            if (lastBlockedNetworkId == -1) {
                int currentNetworkId = wifiManager.getConnectionInfo().getNetworkId();
                disconnectionSuccessful = wifiManager.disableNetwork(currentNetworkId)
                    && wifiManager.removeNetwork(currentNetworkId);
            } else {
                disconnectionSuccessful = wifiManager.enableNetwork(lastBlockedNetworkId, true)
                    && wifiManager.reconnect();
            }

            if (disconnectionSuccessful) {
                callbackContext.success("disconnected");
            } else {
                callbackContext.error("not disconnected");
            }
            return disconnectionSuccessful;
        }
    }
}
