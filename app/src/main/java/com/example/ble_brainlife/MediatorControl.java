package com.example.ble_brainlife;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class MediatorControl implements Mediator {

    public  MainActivity main;
    private BleManager ble;

    public MediatorControl(MainActivity main, BleManager ble) {
        this.main = main;
        this.ble = ble;
        main.mediator = this;
        ble.mediator = this;
    }

    @Override
    public void notify(Object sender, String event) {
        switch (event) {
            case "Switch To Layout":
                SwitchToLayout();
                break;
            case "EnableSwitchButton":
                main.SwitchToLayout.setEnabled(true);
                main.SwitchToLayout.setOnClickListener(v -> SwitchToLayout());
                break;
            // Handle other events as needed
        }
    }

    private void SwitchToLayout() {
        // Switch to the BLE info layout
        main.setContentView(R.layout.ble_contro_layout);

        // Find views in the new layout
        TextView deviceName = main.findViewById(R.id.deviceNameValue);
        TextView deviceAddress = main.findViewById(R.id.deviceAddressValue);
        TextView deviceServices = main.findViewById(R.id.deviceServicesValue);
        Button disconnectButton = main.findViewById(R.id.disconnectButton);
        Button sendSignalButton = main.findViewById(R.id.sendSignalButton);
        Button readSignalButton = main.findViewById(R.id.readSignalButton);
        Button stopSignalButton = main.findViewById(R.id.StopSignalButton);


        // Populate BLE information
        deviceName.setText(ble.getDeviceName());
        deviceAddress.setText(ble.getDeviceAddress());

        // Fetch and display services and characteristics
        String services = ble.getServices();
        String characteristics = ble.getCharacteristics();
        deviceServices.setText(services != null ? services : "None");

        // Handle disconnect button click
        disconnectButton.setOnClickListener(v -> {
            ble.disconnect();
            // Go back to the main layout
            main.setContentView(R.layout.activity_main);
        });

        // Handle send signal button click
        sendSignalButton.setOnClickListener(v -> {
            String hexSignal = "2201230D"; // Hexadecimal data to send
            byte[] signal = hexStringToByteArray(hexSignal); // Convert hex to byte array
            ble.writeToCharacteristic(signal); // Send the byte array to BLE characteristic
        });
        stopSignalButton.setOnClickListener(v -> {
            String hexSignal = "2200220D"; // Hexadecimal data to send
            byte[] signal = hexStringToByteArray(hexSignal); // Convert hex to byte array

            ble.writeToCharacteristic(signal); // Send the byte array to BLE characteristic
            ble.StopDataEvent();
            //printCSVFile();
        });


        // Handle read signal button click
        readSignalButton.setOnClickListener(v -> {
            ble.readFromCharacteristic();
            ble.Marked =true;
        });

    }


    // Helper method to convert a hex string to byte array
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }


}