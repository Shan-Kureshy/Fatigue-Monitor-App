package com.example.ble_simple;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BLE extends Service {

    private Binder binder = new BLEbinder();
    private BluetoothManager manager;
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothDevice device;
    public  String address = "DF:92:4A:A4:B9:2C";
    boolean connected=false;

    public class BLEbinder extends Binder{
        public BLE getService(){
            return BLE.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected=true;
                update("com.example.bluetooth.le.ACTION_GATT_CONNECTED");
                Log.i("BLE", "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i("BLE", "Attempting to start service discovery:" +
                        gatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connected=false;
                Log.i("BLE", "Disconnected from GATT server.");
                update("com.example.bluetooth.le.ACTION_GATT_DISCONNECTED");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                update("com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED");
            } else {
                Log.w("BLE", "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                update("com.example.bluetooth.le.ACTION_DATA_AVAILABLE", characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            update("com.example.bluetooth.le.ACTION_DATA_AVAILABLE", characteristic);
        }
    };


    public boolean init() {
        // set BLE manager and adapter
        manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            Log.e("BLE", "bluetooth service initialization failed (manager).");
            return false;
        }

        adapter = manager.getAdapter();
        if (adapter == null) {
            Log.e("BLE", "bluetooth service initialization failed (adapter).");
            return false;
        }
        return true;
    }

    public boolean connect(){
        device = adapter.getRemoteDevice(address);
        if(device==null){
            Log.e("BLE", "Unable to connect to device");
            return false;
        }
        gatt = device.connectGatt(this, true, gattCallback);
        return true;
    }

    public BluetoothGatt getGatt(){
        return gatt;
    }

    private void update(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void update(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        final String extra = "com.example.bluetooth.le.EXTRA_DATA";
        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        if(characteristic.getUuid().equals(UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"))) {
            int flag = characteristic.getProperties();
            //check format of data
            int format = ((flag & 0x01)!= 0) ? BluetoothGattCharacteristic.FORMAT_UINT16 :
                                               BluetoothGattCharacteristic.FORMAT_UINT8 ;

            final int heartRate = characteristic.getIntValue(format, 1);
            intent.putExtra(extra, String.valueOf(heartRate));
        }
        sendBroadcast(intent);
    }



}