package com.example.ble_brainlife;



public class ByteArrayUtils {



    public static String toHexString(byte[] byteArray) {

        StringBuilder hexString = new StringBuilder("0x");

        for (byte b : byteArray) {

            // Format each byte as a two-digit hexadecimal number

            hexString.append(String.format("%02X ", b));

        }

        return hexString.toString().trim(); // Remove the trailing space

    }

}