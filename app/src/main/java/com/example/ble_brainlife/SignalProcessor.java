package com.example.ble_brainlife;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SignalProcessor {
    final double VREF = 3.3;
    final int OFFSET = 1 << 23;
    final int FULL_SCALE = 1 << 24;
    private static final double AMPLITUDE_GOOD = 150.0;
    private static final double AMPLITUDE_WEAK = 300.0;
    private static final double DECISION_THRESHOLD = 0.7;
    private final int windowSize = 7320; //  30s (1 second @244Hz)
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

    private final List<Double> signalListForFiltering = new ArrayList<>();
    private final List<Double> listEEGSignalHeader24ForFiltering = new ArrayList<>();
    private final List<Double> listEEGSignalHeader26ForFiltering = new ArrayList<>();
    private final ExecutorService testingSignalProcessingExecutor = Executors.newSingleThreadExecutor();

    public String lastReceivedValue = "";
    private String currentAF3SignalQuality = "Waiting for signal...";
    private String currentAF4SignalQuality = "Waiting for signal...";

    public SignalProcessor(Context context) {
        this.context = context;
    }

    public void setLastReceivedValue(String lastReceivedValue) {
        this.lastReceivedValue = lastReceivedValue;
    }

    public String getCurrentAF3SignalQuality() {
        return currentAF3SignalQuality;
    }

    public String getCurrentAF4SignalQuality() {
        return currentAF4SignalQuality;
    }

    public String getLastReceivedValue() {
        return lastReceivedValue;
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
                case "24": // Start byte (0x24 in hex) - EPC1 EEG Signal - AF4 Channel
                    signalProcessor = this::header24Process;
                    break;

                case "25": // Start byte (0x25 in hex) - PPG Signal
                    signalProcessor = this::header25Process;
                    break;

                case "26": // Start byte (0x26 in hex) - EPC2 EEG Signal - AF3 Channel
                    signalProcessor = this::header26Process;
                    break;

                case "27": // Start byte (0x27 in hex) - fNIRS Signal 1
                    signalProcessor = this::header27Process;
                    break;

                case "28": // Start byte (0x28 in hex) - fNIRS Signal 2
                    signalProcessor = this::header28Process;
                    break;


                case "0A": // End byte (0x0A in hex)
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
        setLastReceivedValue(signalData);
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
        double calculatedValue = (signalInteger - OFFSET) * VREF / FULL_SCALE / 2.0 * 1000000;
        header24Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
//        eegSignalFilter(24, calculatedValue);
        testingSignalProcessingExecutor.submit(() -> eegSignalTesting(24, calculatedValue));
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
        double calculatedValue = (signalInteger - OFFSET) * VREF / FULL_SCALE / 2.0 * 1000000;
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
        double calculatedValue = (signalInteger - OFFSET) * VREF / FULL_SCALE / 2.0 * 1000000;
        header26Values.add(asciiSignal);
        calculatedValues.add(calculatedValue);
//        eegSignalFilter(26, calculatedValue);
        testingSignalProcessingExecutor.submit(() -> eegSignalTesting(26, calculatedValue));
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
        double calculatedValue = (signalInteger - OFFSET) * VREF / FULL_SCALE / 2.0 * 1000000;
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
        double calculatedValue = (signalInteger - OFFSET) * VREF / FULL_SCALE / 2.0 * 1000000;
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

//    private void eegSignalFilter(int header, double eegSignal) {
//        if (header == 24) {
//            synchronized (listEEGSignalHeader24ForFiltering) {
//                listEEGSignalHeader24ForFiltering.add(eegSignal);
//                if (listEEGSignalHeader24ForFiltering.size() == windowSize) {
//                    List<Double> window = new ArrayList<>(listEEGSignalHeader24ForFiltering);
//                    listEEGSignalHeader24ForFiltering.clear();
//                    List<Double> filteredEEGSignal = EGGSignalProcessor.preprocessEEG(window).subList(2440, windowSize);
//                    header24FilteredValues.addAll(filteredEEGSignal);
//                }
//            }
//        } else if (header == 26) {
//            synchronized (listEEGSignalHeader26ForFiltering) {
//                listEEGSignalHeader26ForFiltering.add(eegSignal);
//                if (listEEGSignalHeader26ForFiltering.size() == windowSize) {
//                    List<Double> window = new ArrayList<>(listEEGSignalHeader26ForFiltering);
//                    listEEGSignalHeader26ForFiltering.clear();
//                    List<Double> filteredEEGSignal = EGGSignalProcessor.preprocessEEG(window).subList(2440, windowSize);
//                    header26FilteredValues.addAll(filteredEEGSignal);
//                }
//            }
//        }
//    }

    private void eegSignalTesting(int header, double signal) {
        if (header == 24) {
            synchronized (listEEGSignalHeader24ForFiltering) {
                listEEGSignalHeader24ForFiltering.add(signal);
                if (listEEGSignalHeader24ForFiltering.size() == windowSize) {
                    List<Double> window = new ArrayList<>(listEEGSignalHeader24ForFiltering);
                    listEEGSignalHeader24ForFiltering.clear();
                    List<Double> filteredAF4EEGSignal = EGGSignalProcessor.preprocessEEG(window).subList(2440, windowSize);

                    int good = 0, weak = 0, bad = 0;
                    for (Double value : filteredAF4EEGSignal) {
                        if (value < AMPLITUDE_GOOD && value > -AMPLITUDE_GOOD) {
                            good++;
                        } else if (value < AMPLITUDE_WEAK && value > -AMPLITUDE_WEAK) {
                            weak++;
                        } else {
                            bad++;
                        }
                    }

                    int total = filteredAF4EEGSignal.size();
                    double ratioGood = (double) good / total;
                    double ratioWeak = (double) weak / total;
                    double ratioBad = (double) bad / total;

                    Log.d("AF4 EEG Ratio", "Good: " + ratioGood + ", Weak: " + ratioWeak + ", Bad: " + ratioBad);

                    boolean isGoodSegment = ratioGood >= DECISION_THRESHOLD;
                    currentAF4SignalQuality = isGoodSegment ? "GOOD" : "NOT GOOD";
                    Log.d("EEG Quality", "Segment is " + currentAF4SignalQuality);

                    if (context instanceof MainActivity) {
                        MainActivity main = (MainActivity) context;
                        main.runOnUiThread(() -> {;
                            if (main.mediator != null) {
                                main.mediator.notify(this, "UpdateAF4SignalQuality:" + currentAF4SignalQuality);
                            }
                        });
                    }
                }
            }
        } else if (header == 26) {
            synchronized (listEEGSignalHeader26ForFiltering) {
                listEEGSignalHeader26ForFiltering.add(signal);
                if (listEEGSignalHeader26ForFiltering.size() == windowSize) {
                    List<Double> window = new ArrayList<>(listEEGSignalHeader26ForFiltering);
                    listEEGSignalHeader26ForFiltering.clear();
                    List<Double> filteredAF3EEGSignal = EGGSignalProcessor.preprocessEEG(window).subList(2440, windowSize);

                    int good = 0, weak = 0, bad = 0;
                    for (Double value : filteredAF3EEGSignal) {
                        if (value < AMPLITUDE_GOOD && value > -AMPLITUDE_GOOD) {
                            good++;
                        } else if (value < AMPLITUDE_WEAK && value > -AMPLITUDE_WEAK) {
                            weak++;
                        } else {
                            bad++;
                        }
                    }

                    int total = filteredAF3EEGSignal.size();
                    double ratioGood = (double) good / total;
                    double ratioWeak = (double) weak / total;
                    double ratioBad = (double) bad / total;

                    Log.d("AF3 EEG Ratio", "Good: " + ratioGood + ", Weak: " + ratioWeak + ", Bad: " + ratioBad);

                    boolean isGoodSegment = ratioGood >= DECISION_THRESHOLD;
                    currentAF3SignalQuality = isGoodSegment ? "GOOD" : "NOT GOOD";
                    Log.d("EEG Quality", "Segment is " + currentAF3SignalQuality);

                    if (context instanceof MainActivity) {
                        MainActivity main = (MainActivity) context;
                        main.runOnUiThread(() -> {;
                            if (main.mediator != null) {
                                main.mediator.notify(this, "UpdateAF3SignalQuality:" + currentAF3SignalQuality);
                            }
                        });
                    }
                }
            }
        }
    }

    public void shutdownExecutor() {
        testingSignalProcessingExecutor.shutdown();
    }
}
