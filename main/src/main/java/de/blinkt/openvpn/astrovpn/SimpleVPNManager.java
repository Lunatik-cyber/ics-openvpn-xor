/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * SimpleVPNManager - Class for managing the OpenVPN process
 * 
 * Features:
 * - Orchestrates the complete AstroVPN connection workflow
 * - Start/stop OpenVPN process with optimized configuration
 * - Log reading and processing
 * - UI state updates
 */
public class SimpleVPNManager {
    private static final String TAG = "AstroVPN_SimpleVPNManager";
    private static final String TEMP_CONFIG_PREFIX = "astrovpn_temp_";
    
    private final Context context;
    private final AstroVPNProfileManager profileManager;
    private final KeyDownloader keyDownloader;
    private final RemoteSelector remoteSelector;
    private final ExecutorService executor;
    
    private String currentProfileId;
    private ConnectionState currentState = ConnectionState.DISCONNECTED;
    
    public enum ConnectionState {
        DISCONNECTED,
        CONNECTING,
        DOWNLOADING_CONFIG,
        OPTIMIZING_REMOTES,
        STARTING_VPN,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }
    
    public interface ConnectionListener {
        void onStateChanged(ConnectionState state, String message);
        void onError(String error, Throwable cause);
        void onConnected(String profileName);
        void onDisconnected();
    }
    
    public static class ConnectionException extends Exception {
        public ConnectionException(String message) {
            super(message);
        }
        
        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    private ConnectionListener connectionListener;
    
    public SimpleVPNManager(Context context) throws AstroVPNProfileManager.ProfileException {
        this.context = context.getApplicationContext();
        this.profileManager = AstroVPNProfileManager.getInstance(context);
        this.keyDownloader = new KeyDownloader();
        this.remoteSelector = new RemoteSelector();
        this.executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Set the connection state listener
     */
    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    /**
     * Connect to VPN using the specified AstroVPN profile
     */
    public void connectAsync(String profileId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    connect(profileId);
                } catch (Exception e) {
                    Log.e(TAG, "Connection failed", e);
                    setState(ConnectionState.ERROR, "Connection failed: " + e.getMessage());
                    if (connectionListener != null) {
                        connectionListener.onError("Connection failed", e);
                    }
                }
            }
        });
    }
    
    /**
     * Synchronous connection method implementing the AstroVPN workflow
     */
    private void connect(String profileId) throws ConnectionException {
        Log.i(TAG, "Starting AstroVPN connection for profile: " + profileId);
        
        try {
            setState(ConnectionState.CONNECTING, "Initializing connection...");
            
            // Step 1: Get the AstroVPN profile
            AstroVPNProfileManager.StoredProfile storedProfile = profileManager.getProfile(profileId);
            ProfileParser.AstroVPNProfile astroProfile = storedProfile.profile;
            
            Log.i(TAG, "Connecting to: " + astroProfile.name);
            
            // Step 2: Download the .ovpn configuration
            setState(ConnectionState.DOWNLOADING_CONFIG, "Downloading configuration...");
            
            Future<KeyDownloader.DownloadResult> downloadFuture = keyDownloader.downloadConfigAsync(astroProfile);
            KeyDownloader.DownloadResult downloadResult = downloadFuture.get();
            
            Log.i(TAG, "Downloaded config, size: " + downloadResult.ovpnConfig.length() + " characters");
            
            // Step 3: Optimize remote servers
            setState(ConnectionState.OPTIMIZING_REMOTES, "Optimizing server selection...");
            
            Future<RemoteSelector.OptimizationResult> optimizeFuture = 
                    remoteSelector.optimizeConfigAsync(downloadResult.ovpnConfig);
            RemoteSelector.OptimizationResult optimizeResult = optimizeFuture.get();
            
            if (optimizeResult.fastestRemote != null) {
                Log.i(TAG, "Fastest server: " + optimizeResult.fastestRemote.hostname + 
                          " (" + optimizeResult.fastestRemote.pingMs + "ms)");
            }
            
            // Step 4: Create and configure OpenVPN profile
            setState(ConnectionState.STARTING_VPN, "Starting VPN connection...");
            
            VpnProfile vpnProfile = createVpnProfileFromConfig(astroProfile.name, optimizeResult.optimizedConfig);
            
            // Step 5: Set as active profile and start VPN
            profileManager.setActiveProfile(profileId);
            currentProfileId = profileId;
            
            // Start the OpenVPN service
            startOpenVPN(vpnProfile);
            
            setState(ConnectionState.CONNECTED, "Connected to " + astroProfile.name);
            
            if (connectionListener != null) {
                connectionListener.onConnected(astroProfile.name);
            }
            
            Log.i(TAG, "AstroVPN connection completed successfully");
            
        } catch (Exception e) {
            throw new ConnectionException("Connection workflow failed", e);
        }
    }
    
    /**
     * Disconnect from VPN
     */
    public void disconnectAsync() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Disconnection failed", e);
                    if (connectionListener != null) {
                        connectionListener.onError("Disconnection failed", e);
                    }
                }
            }
        });
    }
    
    /**
     * Synchronous disconnection
     */
    private void disconnect() {
        Log.i(TAG, "Starting VPN disconnection");
        
        setState(ConnectionState.DISCONNECTING, "Disconnecting...");
        
        try {
            // Stop OpenVPN service
            VPNLaunchHelper.stopOpenVpn();
            
            // Clear active profile
            if (currentProfileId != null) {
                profileManager.setActiveProfile(null);
                profileManager.setLastConnectedProfileId(currentProfileId);
                currentProfileId = null;
            }
            
            // Clean up temporary files
            cleanupTempFiles();
            
            setState(ConnectionState.DISCONNECTED, "Disconnected");
            
            if (connectionListener != null) {
                connectionListener.onDisconnected();
            }
            
            Log.i(TAG, "VPN disconnection completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error during disconnection", e);
            setState(ConnectionState.ERROR, "Disconnection error: " + e.getMessage());
        }
    }
    
    /**
     * Create a VpnProfile from the optimized OpenVPN configuration
     */
    private VpnProfile createVpnProfileFromConfig(String profileName, String ovpnConfig) 
            throws ConnectionException {
        
        try {
            // Save config to temporary file
            File tempConfigFile = saveTempConfig(ovpnConfig);
            
            // Parse the configuration
            ConfigParser configParser = new ConfigParser();
            configParser.parseConfig(new StringReader(ovpnConfig));
            
            VpnProfile vpnProfile = configParser.convertProfile();
            vpnProfile.mName = profileName + " (AstroVPN)";
            vpnProfile.mProfileCreator = "AstroVPN";
            
            // Store the profile temporarily
            ProfileManager.setTemporaryProfile(context, vpnProfile);
            
            Log.i(TAG, "Created VPN profile: " + vpnProfile.mName);
            return vpnProfile;
            
        } catch (Exception e) {
            throw new ConnectionException("Failed to create VPN profile from config", e);
        }
    }
    
    /**
     * Start the OpenVPN connection
     */
    private void startOpenVPN(VpnProfile profile) throws ConnectionException {
        try {
            Intent intent = VPNLaunchHelper.getStartActivityIntent(context, profile);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                // Direct launch if no user interaction needed
                VPNLaunchHelper.startOpenVpn(profile, context);
            }
        } catch (Exception e) {
            throw new ConnectionException("Failed to start OpenVPN", e);
        }
    }
    
    /**
     * Save configuration to a temporary file
     */
    private File saveTempConfig(String config) throws ConnectionException {
        try {
            File tempDir = new File(context.getCacheDir(), "astrovpn");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            
            File tempFile = File.createTempFile(TEMP_CONFIG_PREFIX, ".ovpn", tempDir);
            
            FileWriter writer = new FileWriter(tempFile);
            writer.write(config);
            writer.close();
            
            Log.d(TAG, "Saved temp config to: " + tempFile.getAbsolutePath());
            return tempFile;
            
        } catch (IOException e) {
            throw new ConnectionException("Failed to save temporary config file", e);
        }
    }
    
    /**
     * Clean up temporary configuration files
     */
    private void cleanupTempFiles() {
        try {
            File tempDir = new File(context.getCacheDir(), "astrovpn");
            if (tempDir.exists()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(TEMP_CONFIG_PREFIX)) {
                            if (file.delete()) {
                                Log.d(TAG, "Deleted temp file: " + file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error cleaning up temp files", e);
        }
    }
    
    /**
     * Update connection state and notify listener
     */
    private void setState(ConnectionState state, String message) {
        currentState = state;
        Log.i(TAG, "State changed: " + state + " - " + message);
        
        if (connectionListener != null) {
            connectionListener.onStateChanged(state, message);
        }
    }
    
    /**
     * Get current connection state
     */
    public ConnectionState getCurrentState() {
        return currentState;
    }
    
    /**
     * Get currently connected profile ID
     */
    public String getCurrentProfileId() {
        return currentProfileId;
    }
    
    /**
     * Check if VPN is connected
     */
    public boolean isConnected() {
        return currentState == ConnectionState.CONNECTED;
    }
    
    /**
     * Check if VPN is connecting
     */
    public boolean isConnecting() {
        return currentState == ConnectionState.CONNECTING ||
               currentState == ConnectionState.DOWNLOADING_CONFIG ||
               currentState == ConnectionState.OPTIMIZING_REMOTES ||
               currentState == ConnectionState.STARTING_VPN;
    }
    
    /**
     * Get connection status from OpenVPN service
     */
    public String getConnectionStatus() {
        VpnStatus.ConnectionStatus status = VpnStatus.getLastCleanLogMessage(context);
        if (status != null) {
            return status.toString();
        }
        return currentState.toString();
    }
    
    /**
     * Shutdown the manager and cleanup resources
     */
    public void shutdown() {
        executor.shutdown();
        keyDownloader.shutdown();
        remoteSelector.shutdown();
        cleanupTempFiles();
    }
}