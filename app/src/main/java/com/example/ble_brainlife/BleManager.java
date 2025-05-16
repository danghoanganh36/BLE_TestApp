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
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.WebSocket;


public class BleManager {

    private static final String TAG = "BleManager";
    private static final String CSV_HEADER = "Signal Integer , Voltage Value, Raw Signal(Hex), Header 24 Data, Header 25 Data, Header 26 Data, Header 27 Data, Header 28 Data";
    public static final UUID SERVICE_UUID = UUID.fromString("a6ed0201-d344-460a-8075-b9e8ec90d71b");
    public static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("a6ed0202-d344-460a-8075-b9e8ec90d71b");
    public static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("a6ed0203-d344-460a-8075-b9e8ec90d71b");

    public static final UUID NORDIC_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NORDIC_READ_CHARACTERISTIC_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NORDIC_WRITE_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID LED_CHARACTERISTIC_UUID = UUID.fromString("a6ed0205-d344-460a-8075-b9e8ec90d71b");

    private WebSocket webSocket;
    private final Context context;
    public BluetoothGatt bluetoothGatt;
    public Mediator mediator;
    private String deviceName;
    private String deviceAddress;
    private String services;
    private String characteristics;
    private File csvFile;
    public SignalProcessor signalProcessor;

    public boolean Marked = false;
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    public BleManager(Context context) {
        this.context = context;
        signalProcessor = new SignalProcessor(context);
        saveCSVFileInPublicDirectory();
    }

    public String getLastReceivedValue() {
        return signalProcessor.getLastReceivedValue();
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
                writer.append(CSV_HEADER + ",DaHell").append("\n");
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

            if (newState == BluetoothGatt.STATE_DISCONNECTING) {
                if (webSocket != null) {
                    webSocket.close(1000, "Stopping posting to cloud.");
                    webSocket = null;
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            logDebug("Services discovered with status: " + status);
            services = "";
            characteristics = "";

            BluetoothGattService service = gatt.getService(NORDIC_SERVICE_UUID);
            if (service != null) {
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    characteristics += characteristic.getUuid().toString() + "\n";
                }

                for (BluetoothGattService discoveredService : gatt.getServices()) {
                    services += discoveredService.getUuid().toString() + "\n";
                }

                BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(NORDIC_READ_CHARACTERISTIC_UUID);
                BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(NORDIC_WRITE_CHARACTERISTIC_UUID);
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
            signalProcessor.addData(receivedData);
            signalProcessor.processStack();
        }

    };

    List<Integer> signalIntegers = new ArrayList<>();
    List<Double> calculatedValues = new ArrayList<>();
    List<String> rawSignals = new ArrayList<>();
    List<String> header24Data = new ArrayList<>();
    List<String> header25Data = new ArrayList<>();
    List<String> header26Data = new ArrayList<>();
    List<String> header27Data = new ArrayList<>();
    List<String> header28Data = new ArrayList<>();

    public void StopDataEvent() {
        saveCsvRowsToCurrentFile();
    }

    private void saveCsvRowsToCurrentFile() {

        signalIntegers = signalProcessor.signalIntegers;
        calculatedValues = signalProcessor.calculatedValues;
        rawSignals = signalProcessor.rawSignals;
        header24Data = signalProcessor.header24Values;
        header25Data = signalProcessor.header25Values;
        header26Data = signalProcessor.header26Values;
        header27Data = signalProcessor.header27Values;
        header28Data = signalProcessor.header28Values;

        Log.i("Saving CSV", "Saving CSV rows to file");
        logExecutor.execute(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (uri != null) {
                    try {
                        ContentResolver contentResolver = context.getContentResolver();
                        try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wa")) {
                            Log.d("SIGNAL LIST SIZE", "rawSignals: " + rawSignals.size()
                                    + ", signalIntegers: " + signalIntegers.size()
                                    + ", calculatedValues: " + calculatedValues.size());
                            if (outputStream != null) {
                                StringBuilder csvContent = new StringBuilder();
                                for (int i = 0; i < calculatedValues.size(); i++) {
                                    csvContent.
                                            // Export SIGNAL INTEGER
                                            append(signalIntegers.get(i)).append(",")

                                            // Export VOLTAGE VALUE
                                            .append(calculatedValues.get(i)).append(",")

                                            // Export RAW SIGNAL
                                            .append(rawSignals.get(i));

                                    // Export Header 24 Data
                                    if (i < header24Data.size()) {
                                        csvContent.append(",").append(header24Data.get(i));
                                    } else {
                                        csvContent.append(","); // Placeholder if no value
                                    }

                                    // Export Header 25 Data
                                    if (i < header25Data.size()) {
                                        csvContent.append(",").append(header25Data.get(i));
                                    } else {
                                        csvContent.append(","); // Placeholder if no value
                                    }

                                    // Export Header 26 Data
                                    if (i < header26Data.size()) {
                                        csvContent.append(",").append(header26Data.get(i));
                                    } else {
                                        csvContent.append(",");
                                    }

                                    // Export Header 27 Data
                                    if (i < header27Data.size()) {
                                        csvContent.append(",").append(header27Data.get(i));
                                    } else {
                                        csvContent.append(",");
                                    }

                                    // Export Header 28 Data
                                    if (i < header28Data.size()) {
                                        csvContent.append(",").append(header28Data.get(i));
                                    } else {
                                        csvContent.append(",");
                                    }

                                    csvContent.append("\n");
                                }
                                outputStream.write(csvContent.toString().getBytes());
                                outputStream.flush();
                            }
                            Log.i("SAVING CSV", "CSV file saved successfully");

                        } catch (IOException e) {
                            Log.e("SAVING CSV ERROR", "Error writing to CSV file", e);
                        }
                    } catch (Exception e) {
                        Log.e("SAVING CSV ERROR", "Error writing to CSV file", e);
                    }
                }
            }

            // Clear lists after saving
            signalIntegers.clear();
            calculatedValues.clear();
            rawSignals.clear();
            header24Data.clear();
            header26Data.clear();
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
            BluetoothGattService service = bluetoothGatt.getService(NORDIC_SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic writeCharacteristic = service.getCharacteristic(NORDIC_WRITE_CHARACTERISTIC_UUID);
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
            BluetoothGattService service = bluetoothGatt.getService(NORDIC_SERVICE_UUID);
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
            BluetoothGattService service = bluetoothGatt.getService(NORDIC_SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic readCharacteristic = service.getCharacteristic(NORDIC_READ_CHARACTERISTIC_UUID);
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
}
