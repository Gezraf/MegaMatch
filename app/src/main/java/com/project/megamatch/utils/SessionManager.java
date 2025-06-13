package com.project.megamatch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.project.megamatch.models.UserRole;

public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "MegaMatchPrefs";
    
    // Session keys
    private static final String KEY_USER_ROLE = "userRole";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_SCHOOL_ID = "schoolId";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    
    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;
    
    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }
    
    /**
     * Create login session
     */
    public void createLoginSession(String username, String schoolId, UserRole role) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_USERNAME, username);
        editor.putString(KEY_SCHOOL_ID, schoolId);
        editor.putString(KEY_USER_ROLE, role.name());
        editor.apply();
        
        Log.d(TAG, "Created login session for " + username + " with role " + role);
    }
    
    /**
     * Get stored session data
     */
    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    public String getUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }
    
    public String getSchoolId() {
        return prefs.getString(KEY_SCHOOL_ID, null);
    }
    
    public UserRole getUserRole() {
        String roleName = prefs.getString(KEY_USER_ROLE, UserRole.GUEST.name());
        try {
            return UserRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid role in preferences: " + roleName);
            return UserRole.GUEST;
        }
    }
    
    /**
     * Clear session details
     */
    public void logout() {
        editor.clear();
        editor.apply();
        Log.d(TAG, "Session cleared - user logged out");
    }
    
    /**
     * Quick check if user has specific role
     */
    public boolean hasRole(UserRole role) {
        return getUserRole() == role;
    }
    
    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(UserRole... roles) {
        UserRole currentRole = getUserRole();
        for (UserRole role : roles) {
            if (currentRole == role) {
                return true;
            }
        }
        return false;
    }
} 