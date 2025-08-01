/*
 * Copyright (c) 2024 AstroVPN Development Team
 * Distributed under the GNU GPL v2 with additional terms.
 */

package de.blinkt.openvpn.astrovpn;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * AstroVPNProfileManager - Class for managing astrovpn:// profiles
 * 
 * Features:
 * - CRUD operations for profiles
 * - Encrypted JSON storage using EncryptedSharedPreferences
 * - Secure profile deletion/modification
 * - Prevention of deleting active profiles
 */
public class AstroVPNProfileManager {
    private static final String TAG = "AstroVPN_ProfileManager";
    private static final String PREFS_NAME = "astrovpn_profiles";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_LAST_CONNECTED_PROFILE_ID = "last_connected_profile_id";
    
    private static AstroVPNProfileManager instance;
    private final SharedPreferences encryptedPrefs;
    private final Context context;
    
    // Profile data structure for storage
    public static class StoredProfile {
        public final String id;
        public final String name;
        public final String astrovpnUrl;
        public final ProfileParser.AstroVPNProfile profile;
        public final long createdTimestamp;
        public final long lastUsedTimestamp;
        
        public StoredProfile(String id, ProfileParser.AstroVPNProfile profile, String astrovpnUrl) {
            this.id = id;
            this.name = profile.name;
            this.profile = profile;
            this.astrovpnUrl = astrovpnUrl;
            this.createdTimestamp = System.currentTimeMillis();
            this.lastUsedTimestamp = System.currentTimeMillis();
        }
        
        public StoredProfile(String id, String name, ProfileParser.AstroVPNProfile profile, 
                           String astrovpnUrl, long createdTimestamp, long lastUsedTimestamp) {
            this.id = id;
            this.name = name;
            this.profile = profile;
            this.astrovpnUrl = astrovpnUrl;
            this.createdTimestamp = createdTimestamp;
            this.lastUsedTimestamp = lastUsedTimestamp;
        }
        
        @Override
        public String toString() {
            return "StoredProfile{" +
                    "id='" + id + '\'' +
                    ", name='" + name + '\'' +
                    ", server='" + profile.server + '\'' +
                    '}';
        }
    }
    
    public static class ProfileException extends Exception {
        public ProfileException(String message) {
            super(message);
        }
        
        public ProfileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    private AstroVPNProfileManager(Context context) throws ProfileException {
        this.context = context.getApplicationContext();
        this.encryptedPrefs = createEncryptedPreferences(this.context);
    }
    
    /**
     * Get singleton instance of the profile manager
     */
    public static synchronized AstroVPNProfileManager getInstance(Context context) throws ProfileException {
        if (instance == null) {
            instance = new AstroVPNProfileManager(context);
        }
        return instance;
    }
    
    /**
     * Create encrypted shared preferences for secure storage
     */
    private SharedPreferences createEncryptedPreferences(Context context) throws ProfileException {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            
            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            throw new ProfileException("Failed to create encrypted storage", e);
        }
    }
    
    /**
     * Add a new AstroVPN profile
     */
    public String addProfile(String astrovpnUrl) throws ProfileException {
        Log.i(TAG, "Adding new AstroVPN profile");
        
        // Parse the profile from URL
        ProfileParser.AstroVPNProfile profile;
        try {
            profile = ProfileParser.parseAstroVpnUrl(astrovpnUrl);
        } catch (ProfileParser.ParseException e) {
            throw new ProfileException("Failed to parse AstroVPN URL", e);
        }
        
        // Check for duplicate names
        if (hasProfileWithName(profile.name)) {
            throw new ProfileException("Profile with name '" + profile.name + "' already exists");
        }
        
        // Generate unique ID
        String profileId = UUID.randomUUID().toString();
        
        // Create stored profile
        StoredProfile storedProfile = new StoredProfile(profileId, profile, astrovpnUrl);
        
        // Save to encrypted storage
        saveProfile(storedProfile);
        
        Log.i(TAG, "Successfully added profile: " + profile.name + " (ID: " + profileId + ")");
        return profileId;
    }
    
    /**
     * Update an existing profile
     */
    public void updateProfile(String profileId, String newAstrovpnUrl) throws ProfileException {
        Log.i(TAG, "Updating profile: " + profileId);
        
        if (!hasProfile(profileId)) {
            throw new ProfileException("Profile not found: " + profileId);
        }
        
        // Check if profile is currently active
        if (isProfileActive(profileId)) {
            throw new ProfileException("Cannot update active profile. Disconnect first.");
        }
        
        // Parse new profile
        ProfileParser.AstroVPNProfile newProfile;
        try {
            newProfile = ProfileParser.parseAstroVpnUrl(newAstrovpnUrl);
        } catch (ProfileParser.ParseException e) {
            throw new ProfileException("Failed to parse new AstroVPN URL", e);
        }
        
        // Get existing profile for metadata
        StoredProfile existingProfile = getProfile(profileId);
        
        // Check for name conflicts (excluding current profile)
        if (!existingProfile.name.equals(newProfile.name) && hasProfileWithName(newProfile.name)) {
            throw new ProfileException("Profile with name '" + newProfile.name + "' already exists");
        }
        
        // Create updated profile
        StoredProfile updatedProfile = new StoredProfile(
                profileId, 
                newProfile.name,
                newProfile, 
                newAstrovpnUrl,
                existingProfile.createdTimestamp,
                System.currentTimeMillis()
        );
        
        // Save updated profile
        saveProfile(updatedProfile);
        
        Log.i(TAG, "Successfully updated profile: " + newProfile.name);
    }
    
    /**
     * Delete a profile
     */
    public void deleteProfile(String profileId) throws ProfileException {
        Log.i(TAG, "Deleting profile: " + profileId);
        
        if (!hasProfile(profileId)) {
            throw new ProfileException("Profile not found: " + profileId);
        }
        
        // Prevent deletion of active profile
        if (isProfileActive(profileId)) {
            throw new ProfileException("Cannot delete active profile. Disconnect first.");
        }
        
        // Remove from storage
        removeProfileFromStorage(profileId);
        
        // Clear last connected if this was it
        if (profileId.equals(getLastConnectedProfileId())) {
            setLastConnectedProfileId(null);
        }
        
        Log.i(TAG, "Successfully deleted profile: " + profileId);
    }
    
    /**
     * Get a profile by ID
     */
    public StoredProfile getProfile(String profileId) throws ProfileException {
        if (TextUtils.isEmpty(profileId)) {
            throw new ProfileException("Profile ID cannot be empty");
        }
        
        List<StoredProfile> profiles = getAllProfiles();
        for (StoredProfile profile : profiles) {
            if (profileId.equals(profile.id)) {
                return profile;
            }
        }
        
        throw new ProfileException("Profile not found: " + profileId);
    }
    
    /**
     * Get all stored profiles
     */
    public List<StoredProfile> getAllProfiles() throws ProfileException {
        try {
            String profilesJson = encryptedPrefs.getString(KEY_PROFILES, "[]");
            JSONArray profilesArray = new JSONArray(profilesJson);
            
            List<StoredProfile> profiles = new ArrayList<>();
            for (int i = 0; i < profilesArray.length(); i++) {
                JSONObject profileObj = profilesArray.getJSONObject(i);
                StoredProfile profile = parseStoredProfile(profileObj);
                profiles.add(profile);
            }
            
            return profiles;
        } catch (JSONException e) {
            throw new ProfileException("Failed to load profiles from storage", e);
        }
    }
    
    /**
     * Check if a profile exists
     */
    public boolean hasProfile(String profileId) {
        try {
            getProfile(profileId);
            return true;
        } catch (ProfileException e) {
            return false;
        }
    }
    
    /**
     * Check if a profile with given name exists
     */
    public boolean hasProfileWithName(String name) {
        try {
            List<StoredProfile> profiles = getAllProfiles();
            for (StoredProfile profile : profiles) {
                if (name.equals(profile.name)) {
                    return true;
                }
            }
            return false;
        } catch (ProfileException e) {
            Log.e(TAG, "Error checking for profile name", e);
            return false;
        }
    }
    
    /**
     * Set the currently active profile
     */
    public void setActiveProfile(String profileId) throws ProfileException {
        if (!TextUtils.isEmpty(profileId) && !hasProfile(profileId)) {
            throw new ProfileException("Cannot set non-existent profile as active: " + profileId);
        }
        
        encryptedPrefs.edit()
                .putString(KEY_ACTIVE_PROFILE_ID, profileId)
                .apply();
        
        Log.i(TAG, "Set active profile: " + profileId);
    }
    
    /**
     * Get the currently active profile ID
     */
    public String getActiveProfileId() {
        return encryptedPrefs.getString(KEY_ACTIVE_PROFILE_ID, null);
    }
    
    /**
     * Check if a profile is currently active
     */
    public boolean isProfileActive(String profileId) {
        String activeId = getActiveProfileId();
        return profileId != null && profileId.equals(activeId);
    }
    
    /**
     * Set the last connected profile
     */
    public void setLastConnectedProfileId(String profileId) {
        encryptedPrefs.edit()
                .putString(KEY_LAST_CONNECTED_PROFILE_ID, profileId)
                .apply();
    }
    
    /**
     * Get the last connected profile ID
     */
    public String getLastConnectedProfileId() {
        return encryptedPrefs.getString(KEY_LAST_CONNECTED_PROFILE_ID, null);
    }
    
    /**
     * Clear all profiles (for testing/reset)
     */
    public void clearAllProfiles() {
        encryptedPrefs.edit()
                .remove(KEY_PROFILES)
                .remove(KEY_ACTIVE_PROFILE_ID)
                .remove(KEY_LAST_CONNECTED_PROFILE_ID)
                .apply();
        Log.i(TAG, "Cleared all profiles");
    }
    
    /**
     * Save a profile to encrypted storage
     */
    private void saveProfile(StoredProfile profile) throws ProfileException {
        try {
            List<StoredProfile> profiles = getAllProfiles();
            
            // Remove existing profile with same ID
            profiles.removeIf(p -> p.id.equals(profile.id));
            
            // Add new/updated profile
            profiles.add(profile);
            
            // Convert to JSON and save
            JSONArray profilesArray = new JSONArray();
            for (StoredProfile p : profiles) {
                profilesArray.put(serializeProfile(p));
            }
            
            encryptedPrefs.edit()
                    .putString(KEY_PROFILES, profilesArray.toString())
                    .apply();
            
        } catch (JSONException e) {
            throw new ProfileException("Failed to save profile to storage", e);
        }
    }
    
    /**
     * Remove a profile from storage
     */
    private void removeProfileFromStorage(String profileId) throws ProfileException {
        try {
            List<StoredProfile> profiles = getAllProfiles();
            profiles.removeIf(p -> p.id.equals(profileId));
            
            JSONArray profilesArray = new JSONArray();
            for (StoredProfile p : profiles) {
                profilesArray.put(serializeProfile(p));
            }
            
            encryptedPrefs.edit()
                    .putString(KEY_PROFILES, profilesArray.toString())
                    .apply();
            
        } catch (JSONException e) {
            throw new ProfileException("Failed to remove profile from storage", e);
        }
    }
    
    /**
     * Serialize a StoredProfile to JSON
     */
    private JSONObject serializeProfile(StoredProfile profile) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", profile.id);
        obj.put("name", profile.name);
        obj.put("astrovpnUrl", profile.astrovpnUrl);
        obj.put("createdTimestamp", profile.createdTimestamp);
        obj.put("lastUsedTimestamp", profile.lastUsedTimestamp);
        return obj;
    }
    
    /**
     * Parse a StoredProfile from JSON
     */
    private StoredProfile parseStoredProfile(JSONObject obj) throws ProfileException, JSONException {
        String id = obj.getString("id");
        String name = obj.getString("name");
        String astrovpnUrl = obj.getString("astrovpnUrl");
        long createdTimestamp = obj.getLong("createdTimestamp");
        long lastUsedTimestamp = obj.getLong("lastUsedTimestamp");
        
        // Parse the profile from URL
        ProfileParser.AstroVPNProfile profile;
        try {
            profile = ProfileParser.parseAstroVpnUrl(astrovpnUrl);
        } catch (ProfileParser.ParseException e) {
            throw new ProfileException("Failed to parse stored profile URL", e);
        }
        
        return new StoredProfile(id, name, profile, astrovpnUrl, createdTimestamp, lastUsedTimestamp);
    }
}