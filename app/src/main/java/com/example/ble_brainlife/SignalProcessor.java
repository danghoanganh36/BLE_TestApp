package com.example.ble_brainlife;

import static android.content.ContentValues.TAG;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

public class SignalProcessor {
    Queue<Byte> dataQueue = new LinkedList<>();
    private Consumer<String> signalProcessor; // Delegate variable
    public List<String> header24Values = new ArrayList<>();
    public List<String> header23Values = new ArrayList<>();

    public List<Integer> signalIntegers = new ArrayList<>();
    public List<Double> calculatedValues = new ArrayList<>(); // Corrected reference
    public List<String> rawSignals = new ArrayList<>();

    public SignalProcessor(){

    }
    void AddData(byte[] data){
        // Push each byte onto the stack
        for (byte b : data) {
            dataQueue.offer(b);
        }
    }
    public void processStack() {
        StringBuilder signalBuilder = new StringBuilder();

        while (!dataQueue.isEmpty()) {
            byte b = dataQueue.poll(); // Pop last received byte
            String byteHex = String.format("%02X", b);

            switch (byteHex) {
                case "24": // Start byte (0x24 in hex)
                    Log.d("Signal", "Start of signal detected");
                    signalProcessor = this::Header24Process;
                    break;
                case "25": // Start byte (0x24 in hex)
                    Log.d("Signal", "Start of signal detected");
                    signalProcessor = this::Header25Process;
                    break;
                case "39":

                    break;

                case "0A": // End byte (0x0A in hex)
                    Log.d("Signal", "End of signal detected");
                    Log.d("Processed Signal", signalBuilder.toString().trim());

                    // Use the delegate to process the extracted signal
                    if (signalProcessor != null) {
                        signalProcessor.accept(signalBuilder.toString().trim());
                    }

                    // Reset signalProcessor to null after processing the signal
                    signalProcessor = null;
                    signalBuilder.setLength(0);
                    break;

                default:
                    signalBuilder.append(byteHex);
                    break;
            }
        }
    }

    private void Header24Process(final String signalData) {

        String asciiSignal = signalData;
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double  calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header24Values.add(asciiSignal);
        /// More and more

    }
    private void Header25Process(final String signalData) {

        String asciiSignal = signalData;
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double  calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header24Values.add(asciiSignal);
        /// More and more

    }

    public void ClearData(){
        // Clear lists after saving
        signalIntegers.clear();
        calculatedValues.clear();
        rawSignals.clear();
        header24Values.clear();
        header23Values.clear();
    }
}
