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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;


public class BleManager {

    private static final String TAG = "BleManager";
    private static final String CSV_HEADER = "Timestamp (ms),Raw Data,Processed Data,HexData, Marked";
    private static final UUID SERVICE_UUID = UUID.fromString("a6ed0201-d344-460a-8075-b9e8ec90d71b");
    private static final UUID READ_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID WRITE_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID LED_CHARACTERISTIC_UUID = UUID.fromString("a6ed0205-d344-460a-8075-b9e8ec90d71b");
    public static final String WEBSOCKET_SERVER_URL = "ws://ws-gateway.dev.brainlife.tech";
    public static final Integer MAX_DATA_SIZE = 1000;
    public static final Integer SAMPLING_RATE = 250;

    private WebSocket webSocket;
    private final Context context;
    public BluetoothGatt bluetoothGatt;
    public Mediator mediator;
    private String deviceName;
    private String deviceAddress;
    private String services;
    private String characteristics;
    private File csvFile;
    private String latestCsvRow;
    public static List<Byte> dataBuffer = new ArrayList<>();
    public static List<Byte> leftoverData = new ArrayList<>();
    private final List<SignalData> collectedSignalData = new ArrayList<>();


    public boolean Marked = false;
    //short MarkedAsNumber = Marked ? 1 : 0;
    // Executor service to handle logging in parallel
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    public BleManager(Context context) {
        this.context = context;
        saveCSVFileInPublicDirectory();
    }

    public String getLatestCsvRow() {
        return latestCsvRow;
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
        String webSocketUrl = WEBSOCKET_SERVER_URL;

        Request request = new Request.Builder().url(webSocketUrl).build();

        OkHttpClient client = new OkHttpClient();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.d(TAG, "WebSocket connection opened: " + response.message());
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                if (response != null) {
                    Log.e(TAG, "WebSocket failure: " + response.message());
                }
                Log.e(TAG, "WebSocket error: " + t.getMessage(), t);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "Message received from server: " + text);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            }
        });
    }

    private void postToCloud(List<SignalData> dataToSend) {
        if (webSocket == null) {
            Log.e("WebSocket", "WebSocket is not connected.");
            return;
        }

        try {
            Log.e("WebSocket", webSocket.toString());

            // Construct JSON payload
            String userId = UUID.randomUUID().toString();

            StringBuilder signalDataJson = new StringBuilder("[");
            for (SignalData signalData : dataToSend) {
                signalDataJson.append(signalData.toJson()).append(",");
            }
            if (signalDataJson.length() > 1) {
                signalDataJson.setLength(signalDataJson.length() - 1);
            }
            signalDataJson.append("]");

            // Create payload JSON
            String payload = "{"
                    + "\"records\": ["
                    + "    {"
                    + "        \"key\": \"ID-BL001\","
                    + "        \"value\": {"
                    + "            \"user_id\": \"" + userId + "\","
                    + "            \"calculatedSignalValues\": " + signalDataJson
                    + "        }"
                    + "    }"
                    + "]"
                    + "}";

//            // Compress and encode payload
//            byte[] compressedPayload = compressWithGzip(payload);
//            String base64Payload = Base64.getEncoder().encodeToString(compressedPayload);

            // Send message via WebSocket
//            webSocket.send(base64Payload);
            Log.e("WebSocket", "Sending payload: " + payload);
            webSocket.send(payload);
            Log.d("WebSocket", "Message sent to Cloud via WebSocket.");
        } catch (Exception e) {
            Log.e(TAG, "Error sending data to WebSocket: " + e.getMessage());
        }
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
            long receivedTimestamp = System.currentTimeMillis();
            // If there's leftover data from the previous signal, append it
            if (!leftoverData.isEmpty()) {
                dataBuffer.addAll(leftoverData);
                leftoverData.clear(); // Clear leftover data
            }

            // Add the new received data to the buffer
            for (byte b : receivedData) {
                dataBuffer.add(b);
            }

            // Process data to detect complete signals
            logReceivedSignal(processBuffer(), receivedTimestamp);
        }
    };

    private String processBuffer() {
        int startIdx = -1;
        int endIdx = -1;

        // Look for start (0x24) and end (0x0A) markers
        for (int i = 0; i < dataBuffer.size(); i++) {
            if (dataBuffer.get(i) == 0x24 && startIdx == -1) { // Start byte found
                startIdx = i;
            } else if (dataBuffer.get(i) == 0x0A && startIdx != -1) { // End byte found after start byte
                endIdx = i;
                break;
            }
        }

        // If we have found a complete signal
        if (startIdx != -1 && endIdx != -1) {
            List<Byte> completeSignalList = dataBuffer.subList(startIdx, endIdx + 1);
            byte[] completeSignal = new byte[completeSignalList.size()];
            for (int i = 0; i < completeSignalList.size(); i++) {
                completeSignal[i] = completeSignalList.get(i);
            }

            dataBuffer.subList(0, endIdx + 1).clear();
            return ByteArrayUtils.toHexString(completeSignal);
            // Remove the processed data from the buffer
        } else {
            // If no complete signal, save the leftover data
            leftoverData = new ArrayList<>(dataBuffer);
            dataBuffer.clear();
        }
        return "";
    }

    private String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }


    Uri csvUri; int signalInteger;String numberString;
    // Format the result
    int MAX_ROWS = 10000;
    long milliseconds ;
    List<String> csvRows = new ArrayList<>(); // List to store CSV rows
    boolean isStoped = false;
    private void logReceivedSignal(final String signalData, final long signalTimestamp) {

        String no0XData = signalData.replace("0x", "");
        String cleanSignalData = signalData.replace("0x", "").replace(" ", "");

        int startIndex = cleanSignalData.indexOf("24");
        if (startIndex == -1) {
            Log.d(TAG, "No start marker (0x24) found.");
            return;
        }

        int endIndex = cleanSignalData.indexOf("0A", startIndex);
        if (endIndex == -1) {
            Log.d(TAG, "No end marker (0A) found.");
            return;
        }

        String signalHex = cleanSignalData.substring(startIndex + 2, endIndex);
        String asciiSignal = hexToAscii(signalHex);

        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            if (!numberString.isEmpty()) {
                signalInteger = Integer.parseInt(numberString);
                Log.d(TAG, "Extracted Integer Signal: " + signalInteger);
            } else {
                Log.d(TAG, "No numeric data found in the extracted signal.");
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }

        double calculatedValue = (signalInteger - 8388608) * 1.6/8388608;

        collectedSignalData.add(new SignalData(calculatedValue, signalTimestamp));

        if (collectedSignalData.size() > MAX_DATA_SIZE) {
            collectedSignalData.subList(0, SAMPLING_RATE).clear();
        }

        if (collectedSignalData.size() == MAX_DATA_SIZE) {
            Log.d("Data size", "Data size reached " + collectedSignalData.size());
            postToCloud(new ArrayList<>(collectedSignalData));
        }

        milliseconds = System.currentTimeMillis();
        String csvRow = milliseconds + "," + signalInteger + "," + calculatedValue + "," + no0XData + "," + (Marked ? 1 : 0) + "\n";
        logDebug(csvRow);
        latestCsvRow = csvRow;
        Marked = false;

        if (!isStoped) {
            csvRows.add(csvRow);

            //Save rows to the current file if the limit is reached
            if (csvRows.size() >= MAX_ROWS) {
                saveCsvRowsToCurrentFile();
            }
        }
    }

    public void StopDataEvent(){
        logDebug("Stop + " + csvRows.size());
        Log.e(TAG,"Stop + " + csvRows.size());
        isStoped = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            if (uri != null) {
                try {
                    ContentResolver contentResolver = context.getContentResolver();
                    try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wa")) { // "wa" for append
                        for (String row : csvRows) {
                            outputStream.write(row.getBytes());
                        }
                        outputStream.flush();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing to CSV file using OutputStream", e);
                }
            } else {
                Log.e(TAG, "Failed to get URI for CSV file");
            }
        } else {
            if (csvFile != null && csvFile.exists()) {
                try (FileOutputStream fileOutputStream = new FileOutputStream(csvFile, true)) { // true for append
                    for (String row : csvRows) {
                        fileOutputStream.write(row.getBytes());

                    }

                    fileOutputStream.flush();

                } catch (IOException e) {
                    Log.e(TAG, "Error writing to CSV file using FileOutputStream", e);
                }
            } else {
                Log.e(TAG, "CSV file does not exist or is null");
            }
        }
        /*if (csvUri != null) {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                try (OutputStream outputStream = contentResolver.openOutputStream(csvUri, "wa")) {
                    if (outputStream != null) {
                        for (String row : csvRows) {
                            outputStream.write(row.getBytes());
                        }
                        outputStream.flush();
                    } else {
                        Log.e(TAG, "OutputStream is null");
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to CSV file using OutputStream", e);
            }
        } else if (csvFile != null && csvFile.exists()) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(csvFile, true)) {
                for (String row : csvRows) {
                    fileOutputStream.write(row.getBytes());

                }

                fileOutputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to CSV file using FileOutputStream", e);
            }
        } else {
            Log.e(TAG, "CSV file does not exist or is null");
        }*/

        csvRows.clear(); // Clear the list after writing
        isStoped = false;
    }

    private void saveCsvRowsToCurrentFile() {
        final List<String> rowsToSave = new ArrayList<>(csvRows); // Copy rows to avoid modification
        csvRows.clear(); // Clear the list after copying

        logExecutor.execute(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                if (uri != null) {
                    try {
                        ContentResolver contentResolver = context.getContentResolver();
                        try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wa")) { // "wa" for append
                            for (String row : rowsToSave) {
                                outputStream.write(row.getBytes());
                            }
                            outputStream.flush();
                            outputStream.close();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to CSV file using OutputStream", e);
                    }
                } else {
                    Log.e(TAG, "Failed to get URI for CSV file");
                }
            }
        });
    }


    private void logReceivedSignalInParallel(final String signalData) {
        logExecutor.execute(() -> {
            // Debugging
            Log.d(TAG, "Signal Data: " + signalData);

            // Extract the number from the signalData
            String numberString = signalData.replaceAll("[^0-9]", ""); // Remove non-numeric characters
            double number;
            try {
                number = Double.parseDouble(numberString);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error parsing number from signal data", e);
                return;
            }

            // Perform the calculation
            double calculatedValue = ((number - 8388608) * 1.2)/8388608;;
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String utcTimestamp = utcFormat.format(new Date());
            // Format the result
            long milliseconds = System.currentTimeMillis(); // Get time in milliseconds
            String csvRow = utcTimestamp   + "," + calculatedValue +"," +  ( (Marked) ? 1 : 0) + "\n";
            logDebug(csvRow);


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android Q and above
                //Uri csvUri =uri;
                if (csvUri != null) {
                    try {
                        ContentResolver contentResolver = context.getContentResolver();
                        try (OutputStream outputStream = contentResolver.openOutputStream(uri, "wa")) { // "wa" for append
                            if (outputStream != null) {
                                outputStream.write(csvRow.getBytes());
                                outputStream.flush();
                            } else {
                                Log.e(TAG, "OutputStream is null");
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to CSV file using OutputStream", e);
                    }
                } else {
                    Log.e(TAG, "Failed to get URI for CSV file");
                }
            } else {
                // For Android versions below Q
                if (csvFile != null && csvFile.exists()) {
                    try (FileOutputStream fileOutputStream = new FileOutputStream(csvFile, true)) { // true for append
                        fileOutputStream.write(csvRow.getBytes());
                        fileOutputStream.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Error writing to CSV file using FileOutputStream", e);
                    }
                } else {
                    Log.e(TAG, "CSV file does not exist or is null");
                }
            }
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
