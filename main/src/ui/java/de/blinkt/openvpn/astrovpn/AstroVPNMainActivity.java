/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import de.blinkt.openvpn.R;

import java.util.ArrayList;
import java.util.List;

/**
 * AstroVPNMainActivity - Modern Material Design 3 main screen
 * 
 * Features:
 * - Minimalist design with Material Design 3
 * - Connection status with visual indication
 * - Profile dropdown and management
 * - Dark/light theme support
 */
public class AstroVPNMainActivity extends AppCompatActivity implements SimpleVPNManager.ConnectionListener {
    
    private static final String TAG = "AstroVPN_MainActivity";
    private static final int REQUEST_VPN_PERMISSION = 1000;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    
    // UI Components
    private MaterialCardView statusCard;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView statusDetails;
    private Spinner profileSpinner;
    private MaterialButton connectButton;
    private MaterialButton addProfileButton;
    private MaterialButton manageButton;
    
    // AstroVPN Components
    private AstroVPNProfileManager profileManager;
    private SimpleVPNManager vpnManager;
    
    // Data
    private List<AstroVPNProfileManager.StoredProfile> profiles = new ArrayList<>();
    private ArrayAdapter<String> profileAdapter;
    private String selectedProfileId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.astrovpn_main_activity);
        
        try {
            initializeAstroVPN();
            initializeUI();
            checkPermissions();
            loadProfiles();
            handleIntent(getIntent());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AstroVPN", e);
            showError("Failed to initialize AstroVPN: " + e.getMessage());
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
        updateConnectionStatus();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vpnManager != null) {
            vpnManager.shutdown();
        }
    }
    
    /**
     * Initialize AstroVPN components
     */
    private void initializeAstroVPN() throws AstroVPNProfileManager.ProfileException {
        profileManager = AstroVPNProfileManager.getInstance(this);
        vpnManager = new SimpleVPNManager(this);
        vpnManager.setConnectionListener(this);
    }
    
    /**
     * Initialize UI components
     */
    private void initializeUI() {
        // Status card
        statusCard = findViewById(R.id.status_card);
        statusIcon = findViewById(R.id.status_icon);
        statusText = findViewById(R.id.status_text);
        statusDetails = findViewById(R.id.status_details);
        
        // Profile selection
        profileSpinner = findViewById(R.id.profile_spinner);
        
        // Buttons
        connectButton = findViewById(R.id.connect_button);
        addProfileButton = findViewById(R.id.add_profile_button);
        manageButton = findViewById(R.id.manage_button);
        
        // Set click listeners
        connectButton.setOnClickListener(this::onConnectClicked);
        addProfileButton.setOnClickListener(this::onAddProfileClicked);
        manageButton.setOnClickListener(this::onManageClicked);
        
        // Profile spinner listener
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position <= profiles.size()) {
                    selectedProfileId = profiles.get(position - 1).id;
                } else {
                    selectedProfileId = null;
                }
                updateConnectButtonState();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedProfileId = null;
                updateConnectButtonState();
            }
        });
        
        // Initialize profile adapter
        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profileSpinner.setAdapter(profileAdapter);
        
        updateConnectionStatus();
    }
    
    /**
     * Check and request necessary permissions
     */
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                    permissions.toArray(new String[0]), 
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }
    
    /**
     * Load profiles and update UI
     */
    private void loadProfiles() {
        try {
            profiles = profileManager.getAllProfiles();
            updateProfileSpinner();
        } catch (AstroVPNProfileManager.ProfileException e) {
            Log.e(TAG, "Failed to load profiles", e);
            showError("Failed to load profiles: " + e.getMessage());
        }
    }
    
    /**
     * Update profile spinner with current profiles
     */
    private void updateProfileSpinner() {
        profileAdapter.clear();
        profileAdapter.add("Select a profile...");
        
        for (AstroVPNProfileManager.StoredProfile profile : profiles) {
            profileAdapter.add(profile.name);
        }
        
        profileAdapter.notifyDataSetChanged();
        
        // Select last connected profile if available
        String lastConnectedId = profileManager.getLastConnectedProfileId();
        if (!TextUtils.isEmpty(lastConnectedId)) {
            for (int i = 0; i < profiles.size(); i++) {
                if (lastConnectedId.equals(profiles.get(i).id)) {
                    profileSpinner.setSelection(i + 1);
                    break;
                }
            }
        }
    }
    
    /**
     * Handle incoming intents (astrovpn:// URLs)
     */
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null && "astrovpn".equals(data.getScheme())) {
                String url = data.toString();
                Log.i(TAG, "Received AstroVPN URL: " + url);
                showAddProfileDialog(url);
            }
        }
    }
    
    /**
     * Connect button click handler
     */
    private void onConnectClicked(View view) {
        if (vpnManager.isConnected()) {
            // Disconnect
            vpnManager.disconnectAsync();
        } else if (!TextUtils.isEmpty(selectedProfileId)) {
            // Connect
            connectToProfile(selectedProfileId);
        }
    }
    
    /**
     * Add profile button click handler
     */
    private void onAddProfileClicked(View view) {
        showAddProfileDialog(null);
    }
    
    /**
     * Manage button click handler
     */
    private void onManageClicked(View view) {
        showManageProfilesDialog();
    }
    
    /**
     * Connect to the specified profile
     */
    private void connectToProfile(String profileId) {
        // Check VPN permission first
        Intent vpnIntent = android.net.VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION);
            return;
        }
        
        vpnManager.connectAsync(profileId);
    }
    
    /**
     * Show add profile dialog
     */
    private void showAddProfileDialog(String prefilledUrl) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_profile, null);
        TextInputLayout urlLayout = dialogView.findViewById(R.id.url_input_layout);
        TextInputEditText urlInput = dialogView.findViewById(R.id.url_input);
        MaterialButton pasteButton = dialogView.findViewById(R.id.paste_button);
        
        if (!TextUtils.isEmpty(prefilledUrl)) {
            urlInput.setText(prefilledUrl);
        }
        
        pasteButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                String pastedText = item.getText().toString();
                if (pastedText.startsWith("astrovpn://")) {
                    urlInput.setText(pastedText);
                } else {
                    Toast.makeText(this, "Clipboard does not contain an AstroVPN URL", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add AstroVPN Profile")
                .setView(dialogView)
                .setPositiveButton("Add", (dialog, which) -> {
                    String url = urlInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) {
                        addProfile(url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Add a new profile
     */
    private void addProfile(String astrovpnUrl) {
        try {
            String profileId = profileManager.addProfile(astrovpnUrl);
            Toast.makeText(this, "Profile added successfully", Toast.LENGTH_SHORT).show();
            loadProfiles();
            
            // Select the newly added profile
            for (int i = 0; i < profiles.size(); i++) {
                if (profileId.equals(profiles.get(i).id)) {
                    profileSpinner.setSelection(i + 1);
                    break;
                }
            }
        } catch (AstroVPNProfileManager.ProfileException e) {
            Log.e(TAG, "Failed to add profile", e);
            showError("Failed to add profile: " + e.getMessage());
        }
    }
    
    /**
     * Show profile management dialog
     */
    private void showManageProfilesDialog() {
        if (profiles.isEmpty()) {
            Toast.makeText(this, "No profiles to manage", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] profileNames = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            profileNames[i] = profiles.get(i).name;
        }
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Manage Profiles")
                .setItems(profileNames, (dialog, which) -> {
                    AstroVPNProfileManager.StoredProfile profile = profiles.get(which);
                    showProfileOptionsDialog(profile);
                })
                .setNegativeButton("Close", null)
                .show();
    }
    
    /**
     * Show options for a specific profile
     */
    private void showProfileOptionsDialog(AstroVPNProfileManager.StoredProfile profile) {
        String[] options = {"Delete Profile", "Copy AstroVPN URL", "View Details"};
        
        new MaterialAlertDialogBuilder(this)
                .setTitle(profile.name)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Delete
                            confirmDeleteProfile(profile);
                            break;
                        case 1: // Copy URL
                            copyProfileUrl(profile);
                            break;
                        case 2: // View details
                            showProfileDetails(profile);
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Confirm profile deletion
     */
    private void confirmDeleteProfile(AstroVPNProfileManager.StoredProfile profile) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Profile")
                .setMessage("Are you sure you want to delete \"" + profile.name + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        profileManager.deleteProfile(profile.id);
                        Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show();
                        loadProfiles();
                    } catch (AstroVPNProfileManager.ProfileException e) {
                        showError("Failed to delete profile: " + e.getMessage());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    /**
     * Copy profile URL to clipboard
     */
    private void copyProfileUrl(AstroVPNProfileManager.StoredProfile profile) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AstroVPN URL", profile.astrovpnUrl);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "AstroVPN URL copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Show profile details
     */
    private void showProfileDetails(AstroVPNProfileManager.StoredProfile profile) {
        String details = "Name: " + profile.name + "\n" +
                        "Server: " + profile.profile.server + "\n" +
                        "Description: " + profile.profile.description + "\n" +
                        "Domain Service: " + profile.profile.domainService + "\n" +
                        "Key URL: " + profile.profile.keyUrl + "\n" +
                        "Created: " + new java.util.Date(profile.createdTimestamp);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Profile Details")
                .setMessage(details)
                .setPositiveButton("OK", null)
                .show();
    }
    
    /**
     * Update connection status display
     */
    private void updateConnectionStatus() {
        SimpleVPNManager.ConnectionState state = vpnManager.getCurrentState();
        
        switch (state) {
            case CONNECTED:
                statusIcon.setImageResource(R.drawable.ic_vpn_connected);
                statusText.setText("Connected");
                statusDetails.setText("Secure connection established");
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_connected));
                connectButton.setText("Disconnect");
                connectButton.setEnabled(true);
                break;
                
            case CONNECTING:
            case DOWNLOADING_CONFIG:
            case OPTIMIZING_REMOTES:
            case STARTING_VPN:
                statusIcon.setImageResource(R.drawable.ic_vpn_connecting);
                statusText.setText("Connecting");
                statusDetails.setText(getStateMessage(state));
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_connecting));
                connectButton.setText("Cancel");
                connectButton.setEnabled(true);
                break;
                
            case DISCONNECTING:
                statusIcon.setImageResource(R.drawable.ic_vpn_disconnecting);
                statusText.setText("Disconnecting");
                statusDetails.setText("Shutting down connection");
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnecting));
                connectButton.setEnabled(false);
                break;
                
            case ERROR:
                statusIcon.setImageResource(R.drawable.ic_vpn_error);
                statusText.setText("Error");
                statusDetails.setText("Connection failed");
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_error));
                connectButton.setText("Retry");
                connectButton.setEnabled(true);
                break;
                
            default: // DISCONNECTED
                statusIcon.setImageResource(R.drawable.ic_vpn_disconnected);
                statusText.setText("Disconnected");
                statusDetails.setText("Not connected to VPN");
                statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_disconnected));
                connectButton.setText("Connect");
                updateConnectButtonState();
                break;
        }
    }
    
    /**
     * Update connect button state based on profile selection
     */
    private void updateConnectButtonState() {
        if (vpnManager.getCurrentState() == SimpleVPNManager.ConnectionState.DISCONNECTED) {
            connectButton.setEnabled(!TextUtils.isEmpty(selectedProfileId));
        }
    }
    
    /**
     * Get user-friendly message for connection state
     */
    private String getStateMessage(SimpleVPNManager.ConnectionState state) {
        switch (state) {
            case DOWNLOADING_CONFIG:
                return "Downloading configuration...";
            case OPTIMIZING_REMOTES:
                return "Finding best server...";
            case STARTING_VPN:
                return "Establishing connection...";
            default:
                return "Connecting...";
        }
    }
    
    /**
     * Show error message
     */
    private void showError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
    
    // SimpleVPNManager.ConnectionListener implementation
    
    @Override
    public void onStateChanged(SimpleVPNManager.ConnectionState state, String message) {
        runOnUiThread(() -> {
            updateConnectionStatus();
            if (!TextUtils.isEmpty(message)) {
                statusDetails.setText(message);
            }
        });
    }
    
    @Override
    public void onError(String error, Throwable cause) {
        runOnUiThread(() -> {
            updateConnectionStatus();
            showError(error);
        });
    }
    
    @Override
    public void onConnected(String profileName) {
        runOnUiThread(() -> {
            updateConnectionStatus();
            Toast.makeText(this, "Connected to " + profileName, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            updateConnectionStatus();
            Toast.makeText(this, "Disconnected from VPN", Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK && !TextUtils.isEmpty(selectedProfileId)) {
                vpnManager.connectAsync(selectedProfileId);
            } else {
                Toast.makeText(this, "VPN permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(this, "Notification permission recommended for VPN status", 
                                     Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}