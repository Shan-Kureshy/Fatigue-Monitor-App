package com.example.ble_simple;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {
    private BLE BLEservice;
    private BluetoothManager bluetoothManager;
    private BluetoothLeScanner bluetoothLeScanner;
    BluetoothAdapter bluetoothAdapter;
    private boolean scanning, connected;
    List<BluetoothGattService> services;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    BluetoothDevice currentDevice;
    BluetoothGattService heartRate, bodyTemp, IMU, battery;
    TextView success, batteryText, heartRateText;
    Button connect;

    private ServiceConnection BLEserviceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service){
            BLEservice = ((BLE.BLEbinder) service).getService();
            if (BLEservice != null) {
                boolean initialized = BLEservice.init();
                if(initialized){
                    BLEservice.connect();
                }
                else{
                    finish();
                }
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            BLEservice = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        success = (TextView)findViewById(R.id.addr);
        batteryText = (TextView)findViewById(R.id.Battery);
        heartRateText = (TextView)findViewById(R.id.heartRate);
        connect = (Button)findViewById(R.id.connectButton);

        //TODO: add more robust permission management
        String[] permissions = new String[]{"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_ADMIN", "android.permission.ACCESS_FINE_LOCATION"};
        requestPermissions(permissions, 1);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice();
            }
        });

        //Initialize bluetooth adapter. adapter will be null if bluetooth is not supported on device
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = null;
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        //enables bluetooth on device
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        //sets up ble service to run in background
        Intent gattServiceIntent = new Intent(this, BLE.class);
        bindService(gattServiceIntent, BLEserviceConnection, Context.BIND_AUTO_CREATE);

        //sets up broadcast receiver to receive service calls
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.bluetooth.le.ACTION_GATT_DISCONNECTED");
        filter.addAction("com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED");
        filter.addAction("com.example.bluetooth.le.ACTION_DATA_AVAILABLE");
        filter.addAction("com.example.bluetooth.le.ACTION_GATT_CONNECTED");
        registerReceiver(updateReceiver,filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(BLEservice!=null) BLEservice.connect();

    }

    @Override
    protected void onPause(){
        super.onPause();
        //unregisterReceiver(updateReceiver);
    }



    //scans device for 10 seconds, then stops
    private void scanLeDevice () {
        if (bluetoothLeScanner != null) {
            if (!scanning) {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        bluetoothLeScanner.stopScan(leScanCallback);
                    }
                }, SCAN_PERIOD);

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
            } else {
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }

    // Device scan callback. Searches for specific device address
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if(result.getDevice().getAddress().equals("DF:92:4A:A4:B9:2C")){
                bluetoothLeScanner.stopScan(leScanCallback);
                success.setText("Success!");
            }
        }
    };

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals("com.example.bluetooth.le.ACTION_GATT_CONNECTED")) {
                connected = true;
            }
            else if (action.equals("com.example.bluetooth.le.ACTION_GATT_DISCONNECTED")) {
                connected = false;
            }
            else if (action.equals("com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED")) {
                BluetoothGatt gatt = BLEservice.getGatt();
                services = gatt.getServices();
                updateGatt();
            }
            else if (action.equals("com.example.bluetooth.le.ACTION_DATA_AVAILABLE")) {
                Log.w("BLE", "Action data available.");
            }
        }
    };

    //Function called whenever gatt services are discovered
    private void updateGatt(){
        for(Iterator<BluetoothGattService> i = services.iterator(); i.hasNext();){
            BluetoothGattService item = i.next();
            UUID uuid = item.getUuid();

            //heart rate
            if(uuid.equals(UUID.fromString("0x180D"))){
                BluetoothGattCharacteristic hr = item.getCharacteristic(UUID.fromString("0x2A37"));
                heartRateText.setText("Battery Level: "+ hr.getValue().toString());
            }
            //battery level
            else if(uuid.equals(UUID.fromString("0x180F"))){
                BluetoothGattCharacteristic bat = item.getCharacteristic(UUID.fromString("0x2A19"));
                batteryText.setText("Battery Level: "+ bat.getValue().toString());
            }
            // TODO: IMU
            else if(uuid.equals(UUID.fromString("0x180D"))){

            }
            // TODO: TEMP
            else if(uuid.equals(UUID.fromString("0x180D"))){

            }
        }
    }
}