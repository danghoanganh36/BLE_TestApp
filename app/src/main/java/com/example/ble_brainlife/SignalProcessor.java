package com.example.ble_brainlife;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;

public class SignalProcessor {
    Queue<Byte> dataQueue = new LinkedList<>();
    private Consumer<String> signalProcessor; // Delegate variable
    private final Context context;
    public List<String> header24Values = new ArrayList<>();
    public List<String> header25Values = new ArrayList<>();
    public List<String> header26Values = new ArrayList<>();
    public List<String> header27Values = new ArrayList<>();
    public List<String> header28Values = new ArrayList<>();

    public List<Integer> signalIntegers = new ArrayList<>();
    public List<Double> calculatedValues = new ArrayList<>(); // Corrected reference
    public List<String> rawSignals = new ArrayList<>();

    public SignalProcessor(Context context) {
        this.context = context;
    }

    void addData(byte[] data) {
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
                case "24": // Start byte (0x24 in hex) - EPC1 EEG Signal
                    signalProcessor = this::header24Process;
                    break;

                case "25": // Start byte (0x25 in hex) - PPG Signal
                    signalProcessor = this::header25Process;
                    break;

                case "26": // Start byte (0x26 in hex) - EPC2 EEG Signal
                    signalProcessor = this::header26Process;
                    break;

                case "27": // Start byte (0x27 in hex) - fNIRS Signal 1
                    signalProcessor = this::header27Process;
                    break;

                case "28": // Start byte (0x28 in hex) - fNIRS Signal 2
                    signalProcessor = this::header28Process;
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

    private void header24Process(final String signalData) {
        MainActivity mainActivity = (MainActivity) context;
        mainActivity.logDebug(signalData);
        String asciiSignal = hexToAscii(signalData);
        String rawSignal = String.format("%s%s%s", "24", signalData, "0A");
        rawSignals.add(rawSignal);
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header24Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);

    }

    private void header25Process(final String signalData) {
        String asciiSignal = hexToAscii(signalData);
        String rawSignal = String.format("%s%s%s", "25", signalData, "0A");
        rawSignals.add(rawSignal);
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header25Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
    }

    private void header26Process(final String signalData) {
        String asciiSignal = hexToAscii(signalData);
        String rawSignal = String.format("%s%s%s", "26", signalData, "0A");
        rawSignals.add(rawSignal);
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header26Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
    }

    private void header27Process(final String signalData) {
        String asciiSignal = hexToAscii(signalData);
        String rawSignal = String.format("%s%s%s", "27", signalData, "0A");
        rawSignals.add(rawSignal);
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header27Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
    }

    private void header28Process(final String signalData) {
        String asciiSignal = hexToAscii(signalData);
        String rawSignal = String.format("%s%s%s", "28", signalData, "0A");
        rawSignals.add(rawSignal);
        int signalInteger = 0;
        try {
            String numberString = asciiSignal.replaceAll("[^0-9]", "");
            signalInteger = (!numberString.isEmpty()) ? Integer.parseInt(numberString) : 0;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error converting ASCII signal to integer: " + e.getMessage());
        }
        signalIntegers.add(signalInteger);
        double calculatedValue = (signalInteger - 8388608) * 3.3 / 8388608;
        header28Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
    }

    public void clearData() {
        // Clear lists after saving
        signalIntegers.clear();
        calculatedValues.clear();
        rawSignals.clear();
        header24Values.clear();
        header25Values.clear();
        header26Values.clear();
        header27Values.clear();
        header28Values.clear();
    }

    private String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }
}
