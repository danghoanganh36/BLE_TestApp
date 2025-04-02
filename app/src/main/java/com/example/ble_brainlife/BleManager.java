package com.example.ble_brainlife;

import android.annotation.SuppressLint;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Stack;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.function.Consumer;

public class BleManager {

    private static final String TAG = "BleManager";
    private static final String CSV_HEADER = "Signal Integer , Voltage Value ,Raw Signal(Hex), Header 24 Data, Header 23 Data";
    private static final UUID SERVICE_UUID = UUID.fromString("a6ed0201-d344-460a-8075-b9e8ec90d71b");
    private static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("a6ed0202-d344-460a-8075-b9e8ec90d71b");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("a6ed0203-d344-460a-8075-b9e8ec90d71b");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID LED_CHARACTERISTIC_UUID = UUID.fromString("a6ed0205-d344-460a-8075-b9e8ec90d71b");

    private final Context context;
    public BluetoothGatt bluetoothGatt;
    public Mediator mediator;
    private String deviceName;
    private String deviceAddress;
    private String services;
    private String characteristics;
    private File csvFile;
    public static List<Byte> dataBuffer = new ArrayList<>();
    public static List<Byte> leftoverData = new ArrayList<>();



    public SignalProcessor signalProcessor;
    public boolean Marked = false;
    //short MarkedAsNumber = Marked ? 1 : 0;
    // Executor service to handle logging in parallel
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    public BleManager(Context context) {
        this.context = context;
        signalProcessor = new SignalProcessor();
        saveCSVFileInPublicDirectory();
    }
    Uri uri;
    private void saveCSVFileInPublicDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "ble_signals_log.csv");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);

            ContentResolver contentResolver = context.getContentResolver();
            uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
            if (uri != null) {
                try {
                    OutputStream outputStream = contentResolver.openOutputStream(uri);
                    if (outputStream != null) {
                        // Write the CSV header
                        outputStream.write((CSV_HEADER).getBytes());
                        outputStream.write("\n".getBytes());
                        outputStream.flush();
                        outputStream.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing CSV file to public Documents directory", e);
                }
            }
        } else {
            File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
            if (!storageDir.exists()) {
                storageDir.mkdirs(); // Create the directory if it doesn't exist
            }
            csvFile = new File(storageDir, "ble_signals_log.csv");
            try (FileWriter writer = new FileWriter(csvFile, true)) {
                writer.append(CSV_HEADER+",DaHell").append("\n");
            } catch (IOException e) {
                Log.e(TAG, "Error creating CSV file in public Documents directory", e);
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void connectToBleDevice(BluetoothDevice device) {
        deviceName = device.getName();
        deviceAddress = device.getAddress();
        logDebug("Connecting to device: " + deviceAddress);
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//            gatt.requestMtu();
            String state = newState == BluetoothGatt.STATE_CONNECTED ? "Connected" : "Disconnected";
            logDebug("Connection state changed: " + state);

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                mediator.notify(this, "EnableSwitchButton");
                gatt.discoverServices();
            }
        }

//        @Override
//        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                Log.d(TAG, "MTU size updated to: " + mtu);
//            } else {
//                Log.e(TAG, "Failed to update MTU size");
//            }
//        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            logDebug("Services discovered with status: " + status);
            services = "";
            characteristics = "";

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            if (service != null) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    characteristics += characteristic.getUuid().toString() + "\n";
                }

                for (BluetoothGattService discoveredService : gatt.getServices()) {
                    services += discoveredService.getUuid().toString() + "\n";
                }

                BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID);
                BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID);
                if (readCharacteristic != null) {
                    gatt.setCharacteristicNotification(readCharacteristic, true);
                    BluetoothGattDescriptor descriptor = readCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        }

        public String stringToHex(String input) {
            StringBuilder hexString = new StringBuilder();
            byte[] bytes = input.getBytes();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] rawData = characteristic.getValue();
                String hexString = bytesToHex(rawData);
                Log.d(TAG, "Characteristic read (hex): " + hexString);
                //logReceivedSignalInParallel(stringToHex(hexString));
                //logReceivedSignal(hexString);
            }
        }



        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            byte[] receivedData = characteristic.getValue();


            signalProcessor.processStack();
        }
    };






    Uri csvUri;

    List<Integer> signalIntegers = new ArrayList<>();
    List<Double> calculatedValues = new ArrayList<>(); // Corrected reference
    List<String> rawSignals = new ArrayList<>();
    List<String> header24Values = new ArrayList<>();
    List<String> header23Values = new ArrayList<>();




    public void StopDataEvent(){
        saveCsvRowsToCurrentFile();
    }

    private void saveCsvRowsToCurrentFile() {

        signalIntegers = signalProcessor.signalIntegers;
        calculatedValues = signalProcessor.calculatedValues;
        header23Values= signalProcessor.header23Values;
        header24Values = signalProcessor.header24Values;


        logExecutor.execute(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (uri != null) {
                    try {
                        ContentResolver contentResolver = context.getContentResolver();
                        try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wa")) {
                            if (outputStream != null) {
                                StringBuilder csvContent = new StringBuilder();
                                for (int i = 0; i < rawSignals.size(); i++) {
                                    csvContent.
                                            append(signalIntegers.get(i)).append(",")
                                            .append(calculatedValues.get(i)).append(",")
                                            .append(rawSignals.get(i)).append(",");

                                    if (i < header24Values.size()) {
                                        csvContent.append(",").append(header24Values.get(i));
                                    } else {
                                        csvContent.append(","); // Placeholder if no value
                                    }

                                    if (i < header23Values.size()) {
                                        csvContent.append(",").append(header23Values.get(i));
                                    } else {
                                        csvContent.append(",");
                                    }

                                    csvContent.append("\n");
                                }
                                outputStream.write(csvContent.toString().getBytes());
                                outputStream.flush();
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to CSV file", e);
                    }
                }
            }

            // Clear lists after saving
            signalIntegers.clear();
            calculatedValues.clear();
            rawSignals.clear();
            header24Values.clear();
            header23Values.clear();
        });
    }


    private void logDebug(final String message) {
        logExecutor.execute(() -> {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            long milliseconds = System.currentTimeMillis();
            String logMessage = timestamp + " (" + milliseconds + " ms) - " + message;

            MainActivity mainActivity = (MainActivity) context;
            mainActivity.logDebug(logMessage);
            Log.d(TAG, logMessage);
        });
    }

    @SuppressLint("MissingPermission")
    public void close() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt = null;
        }
    }

    @SuppressLint("MissingPermission")
    public void writeToCharacteristic(byte[] data) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID);
                if (writeCharacteristic != null) {
                    writeCharacteristic.setValue(data);
                    bluetoothGatt.writeCharacteristic(writeCharacteristic);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void writeToLED(byte[] data) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(LED_CHARACTERISTIC_UUID);
                if (writeCharacteristic != null) {
                    writeCharacteristic.setValue(data);
                    bluetoothGatt.writeCharacteristic(writeCharacteristic);
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    public void readFromCharacteristic() {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(READ_CHARACTERISTIC_UUID);
                if (readCharacteristic != null) {
                    bluetoothGatt.readCharacteristic(readCharacteristic);
                }
            }
        }
    }

    public String getDeviceName() {
        return deviceName != null ? deviceName : "Unknown";
    }

    public String getDeviceAddress() {
        return deviceAddress != null ? deviceAddress : "Unknown";
    }

    public String getServices() {
        return services != null ? services : "None";
    }

    public String getCharacteristics() {
        return characteristics != null ? characteristics : "None";
    }

    public File getCSVFile() {
        return csvFile;
    }
}
