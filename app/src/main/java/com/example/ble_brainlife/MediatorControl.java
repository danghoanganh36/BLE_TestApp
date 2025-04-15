package com.example.ble_brainlife;

import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MediatorControl implements Mediator {
    public MainActivity main;
    private BleManager ble;

    public MediatorControl(MainActivity main, BleManager ble) {
        this.main = main;
        this.ble = ble;
        main.mediator = this;
        ble.mediator = this;
    }

    @Override
    public void notify(Object sender, String event) {
        if (event.startsWith("UpdateReceivedData:")) {
            String newData = event.substring("UpdateReceivedData:".length());
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


    private void SwitchToLayout() {
        // Switch to the BLE info layout
        main.setContentView(R.layout.ble_contro_layout);

        // Tìm các view trong layout mới
        TextView deviceName = main.findViewById(R.id.deviceNameValue);
        TextView deviceAddress = main.findViewById(R.id.deviceAddressValue);
        TextView deviceServices = main.findViewById(R.id.deviceServicesValue);
        EditText sessionTimeValue = main.findViewById(R.id.inputSessionTime);
        // Gán receivedDataValue cho biến thành viên
        Button disconnectButton = main.findViewById(R.id.disconnectButton);
        Button sendSignalButton = main.findViewById(R.id.sendSignalButton);
        Button stopSignalButton = main.findViewById(R.id.StopSignalButton);

        // Populate BLE information
        deviceName.setText(ble.getDeviceName());
        deviceAddress.setText(ble.getDeviceAddress());
        String services = ble.getServices();
        String characteristics = ble.getCharacteristics();
        deviceServices.setText(services != null ? services : "None");

        // Xử lý sự kiện nút bấm
        disconnectButton.setOnClickListener(v -> {
            ble.disconnect();
            main.setContentView(R.layout.activity_main);
        });
        sendSignalButton.setOnClickListener(v -> {
            String hexSignal = "2201230D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            Log.d("SEND DATA", "SEND");
            if (!sessionTimeValue.getText().toString().isEmpty()) {
                int sessionTime = Integer.parseInt(sessionTimeValue.getText().toString());
                handleReceiveSignalDataWithinSessionTime(sessionTime);
            }
        });
        stopSignalButton.setOnClickListener(v -> {
            String hexSignal = "2200220D";
            byte[] signal = hexStringToByteArray(hexSignal);
            ble.writeToCharacteristic(signal);
            ble.StopDataEvent();
        });
    }

    private void handleReceiveSignalDataWithinSessionTime(int sessionTime) {
        if (sessionTime > 0) {
            long delayMillis = (long) sessionTime * 60 * 1000;
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
