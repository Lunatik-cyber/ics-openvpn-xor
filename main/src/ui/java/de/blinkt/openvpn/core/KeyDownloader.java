package de.blinkt.openvpn.core;

import android.os.AsyncTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class KeyDownloader {
    
    public interface DownloadCallback {
        void onSuccess(String ovpnConfig);
        void onError(String error);
    }
    
    public void downloadKey(String urlString, DownloadCallback callback) {
        new DownloadTask(callback).execute(urlString);
    }
    
    private static class DownloadTask extends AsyncTask<String, Void, String> {
        private DownloadCallback callback;
        private String errorMessage;
        
        public DownloadTask(DownloadCallback callback) {
            this.callback = callback;
        }
        
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    errorMessage = "HTTP Error: " + responseCode;
                    return null;
                }
                
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                
                reader.close();
                connection.disconnect();
                
                return result.toString();
                
            } catch (Exception e) {
                errorMessage = e.getMessage();
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(String result) {
            if (result != null && !result.isEmpty()) {
                callback.onSuccess(result);
            } else {
                callback.onError(errorMessage != null ? errorMessage : "Неизвестная ошибка");
            }
        }
    }
}