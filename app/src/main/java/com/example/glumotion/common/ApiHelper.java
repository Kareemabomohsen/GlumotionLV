package com.example.glumotion.common;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Small helper for internet requests (free APIs: Open Food Facts, wger, Nightscout, Gemini).
 * The request runs in a background Thread (the main thread is not allowed to use the internet),
 * and the answer comes back to the main thread with a Handler so we can change the screen.
 */
public class ApiHelper {

    // The screen gets the answer through this interface
    public interface ApiCallback {
        void onSuccess(String response);

        void onError(String error);
    }

    // Handler connected to the main (UI) thread
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // GET request in a background Thread
    public static void get(final String urlText, final ApiCallback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlText);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(20000);
                    // Open Food Facts asks every app to send its name
                    connection.setRequestProperty("User-Agent", "GluMotion-SchoolProject/1.0 (Android)");
                    connection.setRequestProperty("Accept", "application/json");
                    handleResponse(connection, callback);
                } catch (Exception e) {
                    sendError(callback, "No internet connection");
                }
            }
        });
        thread.start();
    }

    // POST request with a JSON body (used by Gemini chat - optional)
    public static void postJson(final String urlText, final String jsonBody, final ApiCallback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlText);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    OutputStream out = connection.getOutputStream();
                    out.write(jsonBody.getBytes("UTF-8"));
                    out.close();
                    handleResponse(connection, callback);
                } catch (Exception e) {
                    sendError(callback, "No internet connection");
                }
            }
        });
        thread.start();
    }

    // Reads the answer: code 200 = success, other = error
    private static void handleResponse(HttpURLConnection connection, ApiCallback callback) throws Exception {
        int code = connection.getResponseCode();
        if (code >= 200 && code < 300) {
            String text = readStream(connection.getInputStream());
            sendSuccess(callback, text);
        } else {
            sendError(callback, "Server error " + code);
        }
        connection.disconnect();
    }

    // Reads all the text from the internet stream
    private static String readStream(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line = reader.readLine();
        while (line != null) {
            builder.append(line);
            line = reader.readLine();
        }
        reader.close();
        return builder.toString();
    }

    // Sends the success to the main thread
    private static void sendSuccess(final ApiCallback callback, final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(text);
            }
        });
    }

    // Sends the error to the main thread
    private static void sendError(final ApiCallback callback, final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }
}
