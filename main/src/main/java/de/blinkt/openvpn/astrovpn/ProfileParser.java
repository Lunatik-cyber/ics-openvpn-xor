/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

/**
 * ProfileParser - Class for validation and parsing of astrovpn:// links
 * 
 * Features:
 * - Base64 decoding
 * - JSON parsing  
 * - Structure validation of required fields
 */
public class ProfileParser {
    private static final String TAG = "AstroVPN_ProfileParser";
    private static final String ASTROVPN_SCHEME = "astrovpn";
    
    // Required fields in AstroVPN profile JSON
    private static final String FIELD_NAME = "name";
    private static final String FIELD_SERVER = "server";
    private static final String FIELD_DOMAIN_IP = "domain_ip"; // Alternative to server
    private static final String FIELD_DOMAIN_SERVICE = "domain_service";
    private static final String FIELD_KEY_URL = "key_url";
    private static final String FIELD_DESCRIPTION = "description";
    
    public static class AstroVPNProfile {
        public final String name;
        public final String server;
        public final String domainService;
        public final String keyUrl;
        public final String description;
        public final JSONObject rawJson;
        
        public AstroVPNProfile(String name, String server, String domainService, 
                              String keyUrl, String description, JSONObject rawJson) {
            this.name = name;
            this.server = server;
            this.domainService = domainService;
            this.keyUrl = keyUrl;
            this.description = description;
            this.rawJson = rawJson;
        }
        
        @Override
        public String toString() {
            return "AstroVPNProfile{" +
                    "name='" + name + '\'' +
                    ", server='" + server + '\'' +
                    ", domainService='" + domainService + '\'' +
                    ", keyUrl='" + keyUrl + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
    
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
        
        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Parse an astrovpn:// URL and extract the profile information
     * 
     * @param url The astrovpn:// URL to parse
     * @return AstroVPNProfile object with parsed data
     * @throws ParseException if URL is invalid or parsing fails
     */
    public static AstroVPNProfile parseAstroVpnUrl(String url) throws ParseException {
        if (TextUtils.isEmpty(url)) {
            throw new ParseException("URL cannot be empty");
        }
        
        // Parse the URI
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            throw new ParseException("Invalid URL format", e);
        }
        
        // Validate scheme
        if (!ASTROVPN_SCHEME.equals(uri.getScheme())) {
            throw new ParseException("URL must use astrovpn:// scheme, got: " + uri.getScheme());
        }
        
        // Extract base64 encoded data from the URL
        String encodedData = extractEncodedData(uri);
        
        // Decode base64
        byte[] decodedBytes;
        try {
            decodedBytes = Base64.decode(encodedData, Base64.DEFAULT);
        } catch (IllegalArgumentException e) {
            throw new ParseException("Failed to decode base64 data", e);
        }
        
        // Convert to string
        String jsonString;
        try {
            jsonString = new String(decodedBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new ParseException("Failed to decode UTF-8 string", e);
        }
        
        // Parse JSON
        JSONObject jsonObject;
        try {
            jsonObject = new JSONObject(jsonString);
        } catch (JSONException e) {
            throw new ParseException("Invalid JSON format", e);
        }
        
        // Validate and extract required fields
        return validateAndExtractProfile(jsonObject);
    }
    
    /**
     * Extract the base64 encoded data from the URI
     * This handles different possible URL formats for astrovpn://
     */
    private static String extractEncodedData(Uri uri) throws ParseException {
        // Try getting from the path first (astrovpn://base64data)
        String path = uri.getPath();
        if (!TextUtils.isEmpty(path)) {
            // Remove leading slash if present
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            if (!TextUtils.isEmpty(path)) {
                return path;
            }
        }
        
        // Try getting from the host (astrovpn://base64data/)
        String host = uri.getHost();
        if (!TextUtils.isEmpty(host)) {
            return host;
        }
        
        // Try getting from query parameter
        String queryData = uri.getQueryParameter("data");
        if (!TextUtils.isEmpty(queryData)) {
            return queryData;
        }
        
        throw new ParseException("No base64 data found in URL");
    }
    
    /**
     * Validate JSON structure and extract profile data
     */
    private static AstroVPNProfile validateAndExtractProfile(JSONObject json) throws ParseException {
        try {
            // Extract key_url (always required)
            String keyUrl = getRequiredString(json, FIELD_KEY_URL);
            
            // Extract domain_service (always required) 
            String domainService = getRequiredString(json, FIELD_DOMAIN_SERVICE);
            
            // Extract server field - can be either 'server' or 'domain_ip'
            String server = null;
            if (json.has(FIELD_SERVER) && !TextUtils.isEmpty(json.optString(FIELD_SERVER))) {
                server = json.getString(FIELD_SERVER);
            } else if (json.has(FIELD_DOMAIN_IP) && !TextUtils.isEmpty(json.optString(FIELD_DOMAIN_IP))) {
                server = json.getString(FIELD_DOMAIN_IP);
            } else {
                throw new ParseException("Missing required field: either 'server' or 'domain_ip' must be provided");
            }
            
            // Extract name (optional, generate from server if not provided)
            String name;
            if (json.has(FIELD_NAME) && !TextUtils.isEmpty(json.optString(FIELD_NAME))) {
                name = json.getString(FIELD_NAME);
            } else {
                // Generate name from server
                name = "AstroVPN (" + server + ")";
            }
            
            String description = json.optString(FIELD_DESCRIPTION, "");
            
            // Additional validation
            validateDomainService(domainService, "domain_service");
            validateUrl(keyUrl, "key_url");
            
            Log.i(TAG, "Successfully parsed AstroVPN profile: " + name);
            
            return new AstroVPNProfile(name, server, domainService, keyUrl, description, json);
            
        } catch (JSONException e) {
            throw new ParseException("Missing or invalid required field", e);
        }
    }
    
    /**
     * Get a required string field from JSON, throwing exception if missing or empty
     */
    private static String getRequiredString(JSONObject json, String field) throws JSONException, ParseException {
        if (!json.has(field)) {
            throw new ParseException("Missing required field: " + field);
        }
        
        String value = json.getString(field);
        if (TextUtils.isEmpty(value)) {
            throw new ParseException("Required field cannot be empty: " + field);
        }
        
        return value;
    }
    
    /**
     * Validate that a string is a valid URL
     */
    private static void validateUrl(String url, String fieldName) throws ParseException {
        try {
            Uri.parse(url);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new ParseException(fieldName + " must be a valid HTTP/HTTPS URL");
            }
        } catch (Exception e) {
            throw new ParseException("Invalid URL in field " + fieldName + ": " + url, e);
        }
    }
    
    /**
     * Validate domain service - can be either full URL or hostname:port format
     */
    private static void validateDomainService(String domainService, String fieldName) throws ParseException {
        if (TextUtils.isEmpty(domainService)) {
            throw new ParseException(fieldName + " cannot be empty");
        }
        
        // Check if it's a full URL format
        if (domainService.startsWith("http://") || domainService.startsWith("https://")) {
            validateUrl(domainService, fieldName);
            return;
        }
        
        // Check if it's hostname:port format
        if (domainService.contains(":")) {
            String[] parts = domainService.split(":");
            if (parts.length == 2) {
                String hostname = parts[0];
                String port = parts[1];
                
                // Basic hostname validation
                if (TextUtils.isEmpty(hostname) || hostname.length() < 3) {
                    throw new ParseException(fieldName + " hostname is too short: " + hostname);
                }
                
                // Basic port validation
                try {
                    int portNum = Integer.parseInt(port);
                    if (portNum < 1 || portNum > 65535) {
                        throw new ParseException(fieldName + " port out of range: " + port);
                    }
                } catch (NumberFormatException e) {
                    throw new ParseException(fieldName + " invalid port number: " + port);
                }
                return;
            }
        }
        
        throw new ParseException(fieldName + " must be either a URL or hostname:port format");
    }
    
    /**
     * Generate an astrovpn:// URL from profile data
     * 
     * @param profile The profile to encode
     * @return astrovpn:// URL string
     */
    public static String generateAstroVpnUrl(AstroVPNProfile profile) {
        try {
            String jsonString = profile.rawJson.toString();
            byte[] encodedBytes = jsonString.getBytes("UTF-8");
            String base64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP);
            return ASTROVPN_SCHEME + "://" + base64;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "Failed to generate AstroVPN URL", e);
            return null;
        }
    }
}