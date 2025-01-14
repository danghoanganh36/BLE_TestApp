package com.example.ble_brainlife;

import okhttp3.OkHttpClient;

public class MessageQueueConnectionManager {
    private static MessageQueueConnectionManager instance;
//    private HttpURLConnection connection;
    private final OkHttpClient client;

    private MessageQueueConnectionManager() {
        client = new OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build();
    }

//    public static synchronized MessageQueueConnectionManager getInstance() {
//        if (instance == null) {
//            instance = new MessageQueueConnectionManager();
//        }
//        return instance;
//    }
//
//    public synchronized HttpURLConnection getConnection(String url) throws IOException {
//        if (connection == null || !Objects.equals(connection.getURL(), new URL(url))) {
//            if (connection != null) {
//                connection.disconnect();
//            }
//            connection = (HttpURLConnection) new URL(url).openConnection();
//            connection.setRequestMethod("POST");
//            connection.setRequestProperty("Content-Type", "application/vnd.kafka.json.v2+json");
//            connection.setDoOutput(true);
//        }
//        return connection;
//    }
//
//    public synchronized void closeConnection() {
//        if (connection != null) {
//            connection.disconnect();
//            connection = null;
//        }
//    }

    public static synchronized MessageQueueConnectionManager getInstance() {
        if (instance == null) {
            instance = new MessageQueueConnectionManager();
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }
}
