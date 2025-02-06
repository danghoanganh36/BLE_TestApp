package com.example.ble_brainlife;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private final Handler handler = new Handler();
    private final long SCAN_PERIOD = 10000; // Scanning duration
    private final int REQUEST_PERMISSIONS = 2;
    private final int REQUEST_ENABLE_BT = 1;

    private ListView deviceListView;
    private ArrayAdapter<String> deviceListAdapter;
    private ArrayList<String> deviceList = new ArrayList<>();
    private TextView debugLog;

    private BleManager bleManager;
    private BluetoothDevice connectedDevice; // Track the connected device

    public TextView receivedDataValue;



    public Mediator mediator;

    public Button SwitchToLayout;



    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    logDebug("Bluetooth connect permission not granted.");
                    return;
                }
                if (device != null && device.getName() != null) {
                    String deviceInfo = "Device found: " + device.getName() + " (" + device.getAddress() + ")";
                    logDebug(deviceInfo); // Add log to debugLog and logcat
                    deviceList.add(deviceInfo);
                    deviceListAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bleManager = new BleManager(this);

        Button scanButton = findViewById(R.id.ScanButtonEle);
        scanButton.setOnClickListener(v -> startScanning());

        Button disconnectButton = findViewById(R.id.Disconnect);
        disconnectButton.setOnClickListener(v -> disconnectDevice());
        SwitchToLayout = (Button)findViewById(R.id.SwitchToControlLayout);
        SwitchToLayout.setEnabled(false);
        //SwitchToLayout.setOnClickListener(v-> );

        debugLog = findViewById(R.id.debugLog);

        deviceListView = findViewById(R.id.deviceListView);
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(deviceListAdapter);

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = deviceList.get(position);
            String deviceAddress = deviceInfo.substring(deviceInfo.indexOf("(") + 1, deviceInfo.indexOf(")"));
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

            // Attempt to connect to the selected device
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                logDebug("Bluetooth connect permission not granted.");
                return;
            }
            connectedDevice = device; // Update the connected device
            bleManager.connectToBleDevice(device);
        });

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothReceiver, filter);

        // Request permissions if not granted
        checkPermissions();

        mediator = new MediatorControl(this,bleManager);
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            logDebug("Requesting permissions...");
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_PERMISSIONS);
        }
    }

    @SuppressLint("SetTextI18n")
    private void startScanning() {
        logDebug("Start Scanning");

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            logDebug("Bluetooth disabled or not available.");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        if (!isLocationEnabled()) {
            logDebug("Location services are disabled.");
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
            return;
        }

        scanLeDevice(true);
        handler.postDelayed(() -> scanLeDevice(false), SCAN_PERIOD);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            logDebug("Starting discovery...");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.startDiscovery();
            }
        } else {
            logDebug("Stopping discovery...");
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.cancelDiscovery();
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothReceiver);
        if (bleManager != null) {
            bleManager.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logDebug("Permissions granted, starting scan...");
                startScanning();
            } else {
                logDebug("Permissions denied.");
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnectDevice() {
        if (bleManager.bluetoothGatt != null) {
            logDebug("Disconnecting from device: " + connectedDevice.getAddress());
            bleManager.disconnect();
            bleManager.close();
            connectedDevice = null;

        } else {
            logDebug("No device connected.");
            Toast.makeText(this, "No device connected to disconnect", Toast.LENGTH_SHORT).show();
        }
    }

    public void logDebug(String message) {
        Log.d("MainActivity", message);
        runOnUiThread(() -> debugLog.append(message + "\n"));
        if(receivedDataValue == null) return;
        runOnUiThread(() -> {
            receivedDataValue.setText(""); // Clear the text in receivedDataValue
        });
        runOnUiThread(() -> receivedDataValue.append(message + "\n"));
    }

    public void updateReceivedData(String data) {
        // Update the TextView with the received data
        receivedDataValue.setText(data);
    }


}