package kr.ac.kaist.nmsl.pushmanager.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;
import org.altbeacon.beacon.logging.LogManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import kr.ac.kaist.nmsl.pushmanager.Constants;
import kr.ac.kaist.nmsl.pushmanager.util.BLEUtil;
import kr.ac.kaist.nmsl.pushmanager.util.UUIDUtil;

public class BLEService extends Service implements BeaconConsumer {
    public static final String BLUETOOTH_NOT_FOUND = "bt_not_found";
    public static final String BLUETOOTH_DISABLED = "bt_disabled";
    public static final String BLUETOOTH_LE_BEACON = "ble_beacon";

    private static final String TAG = "BLEService";

    private static boolean isInitialized = false;

    private BluetoothAdapter btAdapter = null;
    private BeaconManager beaconManager = null;
    private BeaconTransmitter beaconTransmitter = null;
    private Beacon advertisingBeacon = null;

    public BLEService() {
        LogManager.setVerboseLoggingEnabled(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startBLE();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if (BLEUtil.isAdvertisingSupportedDevice(this)) {
            advertiseBluetoothDevice(false);
        }
        scanBluetoothDevices(false);

        super.onDestroy();
    }

    private Beacon createAdvertisingBeacon() {
        String uuid = UUIDUtil.toUUID(BLEUtil.getMacAddress()).toString();
        List<Long> testData = new ArrayList<Long>();
        testData.add(418L);
        testData.add(13L);
        testData.add(42L);

        return new Beacon.Builder()
                .setId1(uuid)
                //.setId2(Constants.BeaconConst.UUID_2)
                //.setId3(Constants.BeaconConst.UUID_3)
                .setManufacturer(Constants.BeaconConst.MANUFACTURER)
                .setTxPower(Constants.BeaconConst.TX_POWER)
                .setDataFields(testData)
                .setExtraDataFields(Constants.BeaconConst.EXTRA_DATA_FIELDS).build();
    }

    public void startBLE() {
        if (initializeBluetooth() && !isInitialized()) {
            isInitialized = true;
            initializeBeacon();
            startBeacon();
        }
    }

    private boolean isInitialized() {
        return isInitialized;
    }

    private void startBeacon() {
        if (BLEUtil.isAdvertisingSupportedDevice(this)) {
            advertiseBluetoothDevice(true);
        }

        scanBluetoothDevices(true);
    }

    private void initializeBeacon() {
        Log.d(TAG, "Initializing BeaconManager");
        BeaconParser beaconParser = new BeaconParser()
                .setBeaconLayout(Constants.BeaconConst.LAYOUT_STRING);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(beaconParser);

        if (BLEUtil.isAdvertisingSupportedDevice(this)) {
            Log.d(TAG, "Initializing BeaconParser - Android Lollipop (or higher version) detected!");

            beaconTransmitter = new BeaconTransmitter(getApplicationContext(),
                    beaconParser);
        }
    }

    public void advertiseBluetoothDevice(boolean isEnabled) {
        if (beaconTransmitter == null) {
            return;
        }

        if (isEnabled) {
            this.advertisingBeacon = createAdvertisingBeacon();
            long now = System.currentTimeMillis();
            Log.d(TAG, "I put time as a sample " + now);
            List<Long> samplePayload = new ArrayList<Long>();
            samplePayload.add(now);
            samplePayload.add(12345L);
            samplePayload.add(67890L);
            this.advertisingBeacon.setExtraDataFields(samplePayload);

            beaconTransmitter.startAdvertising(this.advertisingBeacon);
        } else {
            beaconTransmitter.stopAdvertising();
        }
    }

    private boolean initializeBluetooth() {
        btAdapter = BLEUtil.getBluetoothAdapter();
        if (btAdapter == null) {
            Intent localIntent = new Intent(Constants.INTENT_FILTER_BLE);
            localIntent.putExtra(BLUETOOTH_NOT_FOUND, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(localIntent);
            return false;
        }

        if (!btAdapter.isEnabled()) {
            Intent localIntent = new Intent(Constants.INTENT_FILTER_BLE);
            localIntent.putExtra(BLUETOOTH_DISABLED, true);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(localIntent);
            return false;
        }

        return true;
    }

    public void scanBluetoothDevices(boolean isEnabled) {
        if (beaconManager == null) {
            return;
        }

        if (isEnabled) {
            beaconManager.bind(this);
        } else {
            beaconManager.unbind(this);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons,
                                                Region region) {
                Log.d(TAG, "onBeaconServiceConnect called with " + beacons.size() + " many beacons");

                if (beacons.size() <= 0) {
                    return;
                }

                Intent localIntent = new Intent(Constants.INTENT_FILTER_BLE);

                ArrayList<Beacon> beaconList = new ArrayList<>(beacons);

                Collections.sort(beaconList, new Comparator<Beacon>() {
                    @Override
                    public int compare(Beacon lhs, Beacon rhs) {
                        return -1 * Integer.compare(lhs.getRssi(), rhs.getRssi());
                    }
                });

                localIntent.putExtra(BLUETOOTH_LE_BEACON, beaconList);
                sendBroadcast(localIntent);

            }
        });

        try {
            Region region = new Region(Constants.BeaconConst.UNIQUE_REGION_ID,
                    null, null, null);
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}