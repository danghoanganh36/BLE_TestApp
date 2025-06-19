package com.example.ble_brainlife;

import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MediatorControl implements Mediator {
    public MainActivity main;
    private final BleManager ble;

    public MediatorControl(MainActivity main, BleManager ble) {
        this.main = main;
        this.ble = ble;
        main.mediator = this;
        ble.mediator = this;
    }

    private final Handler signalHandler = new Handler();

    private final Runnable signalRunnable = new Runnable() {
        @Override
        public void run() {
            main.logReceiveSignal();
            // milliseconds
            long SIGNAL_INTERVAL = 20;
            signalHandler.postDelayed(this, SIGNAL_INTERVAL);
        }
    };

    @Override
    public void notify(Object sender, String event) {
        if (event.startsWith("UpdateReceivedData:")) {
            String newData = event.substring("UpdateReceivedData:".length());
            return;
        } else if (event.startsWith("UpdateAF4SignalQuality:")) {
            String quality = event.substring("UpdateAF4SignalQuality:".length());
            updateAF4SignalQuality(quality);
            return;
        } else if (event.startsWith("UpdateAF3SignalQuality:")) {
            String quality = event.substring("UpdateAF3SignalQuality:".length());
            updateAF3SignalQuality(quality);
            return;
        }

        switch (event) {
            case "Switch To Layout":
                SwitchToLayout();
                break;
            case "EnableSwitchButton":
                main.SwitchToLayout.setEnabled(true);
                main.SwitchToLayout.setOnClickListener(v -> SwitchToLayout());
                break;
            // Các trường hợp xử lý khác
        }
    }

    private void updateAF4SignalQuality(String quality) {

        if (main != null) {
            TextView signalQualityText = main.findViewById(R.id.signalQualityAF4Text);
            if (signalQualityText != null) {
                main.runOnUiThread(() -> {
                    signalQualityText.setText(quality);
                });
            }
        }
    }

    private void updateAF3SignalQuality(String quality) {
        if (main != null) {
            TextView signalQualityText = main.findViewById(R.id.signalQualityAF3Text);
            if (signalQualityText != null) {
                main.runOnUiThread(() -> {
                    signalQualityText.setText(quality);
                });
            }
        }
    }

    private void SwitchToLayout() {
        // Switch to the BLE info layout
        main.setContentView(R.layout.ble_contro_layout);

        // Tìm các view trong layout mới
        TextView deviceName = main.findViewById(R.id.deviceNameValue);
        TextView deviceAddress = main.findViewById(R.id.deviceAddressValue);
        TextView deviceServices = main.findViewById(R.id.deviceServicesValue);
        TextView AF4signalQualityText = main.findViewById(R.id.signalQualityAF4Text);
        TextView AF3signalQualityText = main.findViewById(R.id.signalQualityAF3Text);
        main.receivedDataValue = main.findViewById(R.id.receivedDataValue);
        EditText sessionTimeValue = main.findViewById(R.id.inputSessionTime);
        // Gán receivedDataValue cho biến thành viên
        Button sendSignalButton = main.findViewById(R.id.sendSignalButton);
        Button stopSignalButton = main.findViewById(R.id.StopSignalButton);
        Button startTestingSignalButton = main.findViewById(R.id.startTestingSignalButton);
        Button stopTestingSignalButton = main.findViewById(R.id.stopTestingSignalButton);

        // Populate BLE information
        deviceName.setText(ble.getDeviceName());
        deviceAddress.setText(ble.getDeviceAddress());
        String services = ble.getServices();
        String characteristics = ble.getCharacteristics();
        deviceServices.setText(services != null ? services : "None");
        AF3signalQualityText.setText(ble.signalProcessor.getCurrentAF3SignalQuality());
        AF4signalQualityText.setText(ble.signalProcessor.getCurrentAF4SignalQuality());

        // Actually Start session
        sendSignalButton.setOnClickListener(v -> {
            String hexSignal = "2201230D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            Log.d("SEND DATA", "SEND");
            if (!sessionTimeValue.getText().toString().isEmpty()) {
                int sessionTime = Integer.parseInt(sessionTimeValue.getText().toString());
                handleReceiveSignalDataWithinSessionTime(sessionTime);
            }
            signalHandler.post(signalRunnable);
        });
        stopSignalButton.setOnClickListener(v -> {
            String hexSignal = "2200220D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            ble.StopDataEvent();
            signalHandler.removeCallbacks(signalRunnable);
        });

        // Testing Start session
        startTestingSignalButton.setOnClickListener(v -> {
            String hexSignal = "2201230D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            Log.d("SEND DATA", "START SEND");
            handleReceiveSignalDataWithinSessionTime(20);
            signalHandler.post(signalRunnable);
            Log.d("SEND DATA", "STOP SEND");
        });
        stopTestingSignalButton.setOnClickListener(v -> {
            String hexSignal = "2200220D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            ble.StopDataEvent();
            signalHandler.removeCallbacks(signalRunnable);
        });
    }

    private void handleReceiveSignalDataWithinSessionTime(int sessionTime) {
        if (sessionTime > 0) {
            long delayMillis = (long) sessionTime * 1000;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                String hexSignal = "2200220D";
                byte[] signal = hexStringToByteArray(hexSignal);
                ble.writeToCharacteristic(signal);
                ble.StopDataEvent();
            }, delayMillis);
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

}
