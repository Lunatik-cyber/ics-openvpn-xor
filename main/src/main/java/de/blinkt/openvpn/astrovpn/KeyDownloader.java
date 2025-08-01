/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * KeyDownloader - Class for downloading .ovpn configs
 * 
 * Features:
 * - Getting IP for substitution via domain_service
 * - Forming key link with domain substitution
 * - Downloading .ovpn config via HTTP
 */
public class KeyDownloader {
    private static final String TAG = "AstroVPN_KeyDownloader";
    private static final int TIMEOUT_CONNECT = 15000; // 15 seconds
    private static final int TIMEOUT_READ = 30000; // 30 seconds
    private static final int MAX_CONFIG_SIZE = 1024 * 1024; // 1MB max config size
    
    private final ExecutorService executor;
    
    public KeyDownloader() {
        this.executor = Executors.newCachedThreadPool();
    }
    
    public static class DownloadException extends Exception {
        public DownloadException(String message) {
            super(message);
        }
        
        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class DownloadResult {
        public final String ovpnConfig;
        public final String resolvedIp;
        public final String originalUrl;
        
        public DownloadResult(String ovpnConfig, String resolvedIp, String originalUrl) {
            this.ovpnConfig = ovpnConfig;
            this.resolvedIp = resolvedIp;
            this.originalUrl = originalUrl;
        }
    }
    
    /**
     * Download .ovpn configuration for the given AstroVPN profile
     * 
     * @param profile The AstroVPN profile containing domain_service and key_url
     * @return DownloadResult containing the config and metadata
     * @throws DownloadException if download fails
     */
    public Future<DownloadResult> downloadConfigAsync(ProfileParser.AstroVPNProfile profile) {
        return executor.submit(new Callable<DownloadResult>() {
            @Override
            public DownloadResult call() throws Exception {
                return downloadConfig(profile);
            }
        });
    }
    
    /**
     * Synchronous version of config download
     */
    public DownloadResult downloadConfig(ProfileParser.AstroVPNProfile profile) throws DownloadException {
        Log.i(TAG, "Starting config download for profile: " + profile.name);
        
        // Step 1: Get IP for domain substitution
        String resolvedIp = getResolvedIpFromDomainService(profile.domainService);
        Log.i(TAG, "Resolved IP: " + resolvedIp);
        
        // Step 2: Form the key URL with IP substitution
        String keyUrl = substituteIpInKeyUrl(profile.keyUrl, resolvedIp);
        Log.i(TAG, "Key URL with IP substitution: " + keyUrl);
        
        // Step 3: Download the .ovpn config
        String ovpnConfig = downloadOvpnConfig(keyUrl);
        Log.i(TAG, "Successfully downloaded ovpn config, size: " + ovpnConfig.length() + " chars");
        
        return new DownloadResult(ovpnConfig, resolvedIp, keyUrl);
    }
    
    /**
     * Get IP address from domain_service endpoint
     * Expected response format: {"ip": "192.168.1.1"}
     */
    private String getResolvedIpFromDomainService(String domainServiceUrl) throws DownloadException {
        Log.d(TAG, "Querying domain service: " + domainServiceUrl);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(domainServiceUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_CONNECT);
            connection.setReadTimeout(TIMEOUT_READ);
            connection.setRequestProperty("User-Agent", "AstroVPN/1.0");
            connection.setRequestProperty("Accept", "application/json");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new DownloadException("Domain service returned HTTP " + responseCode);
            }
            
            String responseJson = readResponseAsString(connection.getInputStream());
            Log.d(TAG, "Domain service response: " + responseJson);
            
            // Parse JSON response to extract IP
            try {
                JSONObject jsonResponse = new JSONObject(responseJson);
                if (!jsonResponse.has("ip")) {
                    throw new DownloadException("Domain service response missing 'ip' field");
                }
                
                String ip = jsonResponse.getString("ip");
                if (TextUtils.isEmpty(ip)) {
                    throw new DownloadException("Domain service returned empty IP");
                }
                
                // Basic IP validation
                if (!isValidIpAddress(ip)) {
                    throw new DownloadException("Domain service returned invalid IP: " + ip);
                }
                
                return ip;
                
            } catch (JSONException e) {
                throw new DownloadException("Failed to parse domain service JSON response", e);
            }
            
        } catch (UnknownHostException e) {
            throw new DownloadException("Cannot resolve domain service host", e);
        } catch (IOException e) {
            throw new DownloadException("Network error querying domain service", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Substitute domain in key_url with the resolved IP address
     * Replaces the hostname in the URL with the IP while preserving the path
     */
    private String substituteIpInKeyUrl(String keyUrl, String ip) throws DownloadException {
        try {
            Uri originalUri = Uri.parse(keyUrl);
            String scheme = originalUri.getScheme();
            int port = originalUri.getPort();
            String path = originalUri.getPath();
            String query = originalUri.getQuery();
            String fragment = originalUri.getFragment();
            
            // Build new URL with IP substitution
            Uri.Builder builder = new Uri.Builder()
                    .scheme(scheme)
                    .authority(port != -1 ? ip + ":" + port : ip);
            
            if (!TextUtils.isEmpty(path)) {
                builder.path(path);
            }
            if (!TextUtils.isEmpty(query)) {
                builder.query(query);
            }
            if (!TextUtils.isEmpty(fragment)) {
                builder.fragment(fragment);
            }
            
            return builder.build().toString();
            
        } catch (Exception e) {
            throw new DownloadException("Failed to substitute IP in key URL", e);
        }
    }
    
    /**
     * Download the .ovpn configuration file from the key URL
     */
    private String downloadOvpnConfig(String keyUrl) throws DownloadException {
        Log.d(TAG, "Downloading ovpn config from: " + keyUrl);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(keyUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_CONNECT);
            connection.setReadTimeout(TIMEOUT_READ);
            connection.setRequestProperty("User-Agent", "AstroVPN/1.0");
            connection.setRequestProperty("Accept", "text/plain, application/x-openvpn-profile");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new DownloadException("Key server returned HTTP " + responseCode);
            }
            
            // Check content length
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_CONFIG_SIZE) {
                throw new DownloadException("Config file too large: " + contentLength + " bytes");
            }
            
            String config = readResponseAsString(connection.getInputStream());
            
            // Basic validation that this looks like an OpenVPN config
            if (!isValidOvpnConfig(config)) {
                throw new DownloadException("Downloaded content does not appear to be a valid OpenVPN config");
            }
            
            return config;
            
        } catch (UnknownHostException e) {
            throw new DownloadException("Cannot resolve key server host", e);
        } catch (IOException e) {
            throw new DownloadException("Network error downloading config", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Read HTTP response as string with size limit
     */
    private String readResponseAsString(InputStream inputStream) throws IOException, DownloadException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        int totalChars = 0;
        
        while ((line = reader.readLine()) != null) {
            totalChars += line.length();
            if (totalChars > MAX_CONFIG_SIZE) {
                throw new DownloadException("Response too large");
            }
            response.append(line).append("\n");
        }
        
        return response.toString().trim();
    }
    
    /**
     * Basic IP address validation (IPv4)
     */
    private boolean isValidIpAddress(String ip) {
        if (TextUtils.isEmpty(ip)) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Basic validation that content looks like an OpenVPN config
     */
    private boolean isValidOvpnConfig(String config) {
        if (TextUtils.isEmpty(config)) {
            return false;
        }
        
        String lowerConfig = config.toLowerCase();
        
        // Check for common OpenVPN config directives
        return lowerConfig.contains("client") || 
               lowerConfig.contains("remote") ||
               lowerConfig.contains("dev tun") ||
               lowerConfig.contains("dev tap") ||
               lowerConfig.contains("proto");
    }
    
    /**
     * Shutdown the executor when no longer needed
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}