/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RemoteSelector - Class for optimizing remote server selection
 * 
 * Features:
 * - Parsing all remote entries from .ovpn config
 * - Pinging each remote server
 * - Sorting by minimum ping time and modifying config
 */
public class RemoteSelector {
    private static final String TAG = "AstroVPN_RemoteSelector";
    private static final int PING_TIMEOUT = 5000; // 5 seconds
    private static final int MAX_CONCURRENT_PINGS = 10;
    private static final int INVALID_PING = Integer.MAX_VALUE;
    
    // Pattern to match OpenVPN remote directive
    // Format: remote <hostname> [port] [proto]
    private static final Pattern REMOTE_PATTERN = Pattern.compile(
        "^\\s*remote\\s+(\\S+)(?:\\s+(\\d+))?(?:\\s+(tcp|udp))?", 
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    
    private final ExecutorService executor;
    
    public RemoteSelector() {
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT_PINGS);
    }
    
    public static class RemoteServer {
        public final String hostname;
        public final int port;
        public final String protocol;
        public final String originalLine;
        public int pingMs = INVALID_PING;
        
        public RemoteServer(String hostname, int port, String protocol, String originalLine) {
            this.hostname = hostname;
            this.port = port;
            this.protocol = protocol;
            this.originalLine = originalLine;
        }
        
        @Override
        public String toString() {
            return "RemoteServer{" +
                    "hostname='" + hostname + '\'' +
                    ", port=" + port +
                    ", protocol='" + protocol + '\'' +
                    ", pingMs=" + (pingMs == INVALID_PING ? "N/A" : pingMs) +
                    '}';
        }
        
        /**
         * Generate the remote line for OpenVPN config
         */
        public String toConfigLine() {
            StringBuilder line = new StringBuilder("remote ").append(hostname);
            if (port > 0) {
                line.append(" ").append(port);
            }
            if (!TextUtils.isEmpty(protocol)) {
                line.append(" ").append(protocol);
            }
            return line.toString();
        }
    }
    
    public static class OptimizationResult {
        public final String optimizedConfig;
        public final List<RemoteServer> sortedRemotes;
        public final RemoteServer fastestRemote;
        
        public OptimizationResult(String optimizedConfig, List<RemoteServer> sortedRemotes) {
            this.optimizedConfig = optimizedConfig;
            this.sortedRemotes = sortedRemotes;
            this.fastestRemote = sortedRemotes.isEmpty() ? null : sortedRemotes.get(0);
        }
    }
    
    /**
     * Optimize the OpenVPN configuration by sorting remotes by ping time
     * 
     * @param ovpnConfig The original OpenVPN configuration
     * @return OptimizationResult with sorted remotes and modified config
     */
    public Future<OptimizationResult> optimizeConfigAsync(String ovpnConfig) {
        return executor.submit(new Callable<OptimizationResult>() {
            @Override
            public OptimizationResult call() throws Exception {
                return optimizeConfig(ovpnConfig);
            }
        });
    }
    
    /**
     * Synchronous version of config optimization
     */
    public OptimizationResult optimizeConfig(String ovpnConfig) {
        Log.i(TAG, "Starting remote optimization");
        
        // Parse all remote entries from config
        List<RemoteServer> remotes = parseRemoteServers(ovpnConfig);
        Log.i(TAG, "Found " + remotes.size() + " remote servers");
        
        if (remotes.isEmpty()) {
            Log.w(TAG, "No remote servers found in config");
            return new OptimizationResult(ovpnConfig, remotes);
        }
        
        // Ping all remotes concurrently
        pingAllRemotes(remotes);
        
        // Sort by ping time (fastest first)
        List<RemoteServer> sortedRemotes = new ArrayList<>(remotes);
        Collections.sort(sortedRemotes, new Comparator<RemoteServer>() {
            @Override
            public int compare(RemoteServer a, RemoteServer b) {
                return Integer.compare(a.pingMs, b.pingMs);
            }
        });
        
        // Log results
        logPingResults(sortedRemotes);
        
        // Generate optimized config
        String optimizedConfig = rebuildConfigWithSortedRemotes(ovpnConfig, remotes, sortedRemotes);
        
        Log.i(TAG, "Remote optimization completed");
        return new OptimizationResult(optimizedConfig, sortedRemotes);
    }
    
    /**
     * Parse remote server entries from OpenVPN config
     */
    private List<RemoteServer> parseRemoteServers(String config) {
        List<RemoteServer> remotes = new ArrayList<>();
        Matcher matcher = REMOTE_PATTERN.matcher(config);
        
        while (matcher.find()) {
            String hostname = matcher.group(1);
            String portStr = matcher.group(2);
            String protocol = matcher.group(3);
            
            int port = 1194; // Default OpenVPN port
            if (!TextUtils.isEmpty(portStr)) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid port in remote line: " + portStr);
                }
            }
            
            if (TextUtils.isEmpty(protocol)) {
                protocol = "udp"; // Default protocol
            }
            
            String originalLine = matcher.group(0);
            RemoteServer remote = new RemoteServer(hostname, port, protocol, originalLine);
            remotes.add(remote);
            
            Log.d(TAG, "Parsed remote: " + remote);
        }
        
        return remotes;
    }
    
    /**
     * Ping all remote servers concurrently
     */
    private void pingAllRemotes(List<RemoteServer> remotes) {
        List<Future<Void>> futures = new ArrayList<>();
        
        for (final RemoteServer remote : remotes) {
            Future<Void> future = executor.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    remote.pingMs = pingRemote(remote);
                    return null;
                }
            });
            futures.add(future);
        }
        
        // Wait for all pings to complete
        for (Future<Void> future : futures) {
            try {
                future.get(PING_TIMEOUT + 1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Log.w(TAG, "Ping future failed", e);
            }
        }
    }
    
    /**
     * Ping a single remote server using TCP socket connection
     */
    private int pingRemote(RemoteServer remote) {
        Log.d(TAG, "Pinging " + remote.hostname + ":" + remote.port);
        
        long startTime = System.currentTimeMillis();
        Socket socket = null;
        
        try {
            // Resolve hostname to IP first
            InetAddress address = InetAddress.getByName(remote.hostname);
            
            // Create socket and attempt connection
            socket = new Socket();
            socket.connect(new InetSocketAddress(address, remote.port), PING_TIMEOUT);
            
            long endTime = System.currentTimeMillis();
            int pingMs = (int) (endTime - startTime);
            
            Log.d(TAG, "Ping successful: " + remote.hostname + " = " + pingMs + "ms");
            return pingMs;
            
        } catch (UnknownHostException e) {
            Log.w(TAG, "Cannot resolve hostname: " + remote.hostname);
            return INVALID_PING;
        } catch (IOException e) {
            Log.w(TAG, "Ping failed for " + remote.hostname + ": " + e.getMessage());
            return INVALID_PING;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    // Ignore close errors
                }
            }
        }
    }
    
    /**
     * Log ping results for debugging
     */
    private void logPingResults(List<RemoteServer> sortedRemotes) {
        Log.i(TAG, "Ping results (sorted by speed):");
        for (int i = 0; i < sortedRemotes.size(); i++) {
            RemoteServer remote = sortedRemotes.get(i);
            String pingStr = remote.pingMs == INVALID_PING ? "FAILED" : remote.pingMs + "ms";
            Log.i(TAG, String.format("  %d. %s:%d = %s", i + 1, remote.hostname, remote.port, pingStr));
        }
    }
    
    /**
     * Rebuild the OpenVPN config with remotes sorted by ping time
     */
    private String rebuildConfigWithSortedRemotes(String originalConfig, 
                                                 List<RemoteServer> originalRemotes,
                                                 List<RemoteServer> sortedRemotes) {
        
        String modifiedConfig = originalConfig;
        
        // Remove all original remote lines
        for (RemoteServer remote : originalRemotes) {
            modifiedConfig = modifiedConfig.replace(remote.originalLine, "");
        }
        
        // Clean up extra newlines
        modifiedConfig = modifiedConfig.replaceAll("\\n\\s*\\n", "\n");
        
        // Add sorted remotes at the beginning of the config
        StringBuilder remoteSection = new StringBuilder();
        remoteSection.append("# Remotes sorted by ping time (fastest first)\n");
        
        for (RemoteServer remote : sortedRemotes) {
            remoteSection.append(remote.toConfigLine());
            if (remote.pingMs != INVALID_PING) {
                remoteSection.append("  # ").append(remote.pingMs).append("ms");
            } else {
                remoteSection.append("  # ping failed");
            }
            remoteSection.append("\n");
        }
        
        remoteSection.append("\n");
        
        // Insert sorted remotes after client directive or at the beginning
        if (modifiedConfig.contains("client")) {
            modifiedConfig = modifiedConfig.replaceFirst("client", "client\n\n" + remoteSection);
        } else {
            modifiedConfig = remoteSection + modifiedConfig;
        }
        
        return modifiedConfig;
    }
    
    /**
     * Get the fastest responding remote server
     */
    public RemoteServer getFastestRemote(List<RemoteServer> remotes) {
        if (remotes.isEmpty()) {
            return null;
        }
        
        RemoteServer fastest = null;
        int fastestPing = INVALID_PING;
        
        for (RemoteServer remote : remotes) {
            if (remote.pingMs < fastestPing) {
                fastestPing = remote.pingMs;
                fastest = remote;
            }
        }
        
        return fastest;
    }
    
    /**
     * Shutdown the executor when no longer needed
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}