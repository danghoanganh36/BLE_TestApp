package com.example.ble_brainlife;

public class SignalData {
//    private final int orderOfData;
    private final double signal;
    private final long timestamp;

    public SignalData(double signal, long timestamp) {
//        this.orderOfData = orderOfData;
        this.signal = signal;
        this.timestamp = timestamp;
    }

//    public int getOrderOfData() { return orderOfData; }

    public double getSignal() {
        return signal;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String toJson() {
        return "{"
//                + "\"signalId\": " + orderOfData + ", "
                + "\"signal\": " + signal + ", "
                + "\"timestamp\": " + timestamp
                + "}";
    }
}
