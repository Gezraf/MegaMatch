package com.project.megamatch.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.project.megamatch.R;
import com.project.megamatch.models.UserRole;

import java.util.HashMap;
import java.util.Map;

public class HelpManager {
    private static final String TAG = "HelpManager";
    
    // Help content for different screens and roles
    private static final Map<String, Map<UserRole, String>> helpContent = new HashMap<>();
    
    static {
        // Initialize help content for different screens
        
        // Login page help
        Map<UserRole, String> loginHelp = new HashMap<>();
        loginHelp.put(UserRole.GUEST, "• לחץ על 'צפייה במגמות' לגישה חופשית למגמות\n• לחץ על 'כניסת רכז' להתחברות כרכז\n• לחץ על 'כניסת מנהל' להתחברות כמנהל");
        loginHelp.put(UserRole.RAKAZ, "• הזן את פרטי ההתחברות שלך\n• אם אין לך חשבון, לחץ על 'אין לי חשבון'\n• פנה למנהל בית הספר לקבלת הרשאות");
        loginHelp.put(UserRole.MANAGER, "• הזן את שם המשתמש והסיסמה שקיבלת\n• צור קשר עם מנהל המערכת אם שכחת את פרטי ההתחברות");
        loginHelp.put(UserRole.ADMIN, "• גש לדף הניהול דרך כפתור ההגדרות\n• נהל בתי ספר ומנהלים במערכת");
        helpContent.put("login", loginHelp);
        
        // School selection help
        Map<UserRole, String> schoolHelp = new HashMap<>();
        schoolHelp.put(UserRole.GUEST, "• בחר בית ספר מהרשימה לצפייה במגמות\n• השתמש בחיפוש למציאה מהירה");
        schoolHelp.put(UserRole.RAKAZ, "• בחר את בית הספר שלך\n• וודא שאתה מורשה לנהל מגמות בבית הספר");
        schoolHelp.put(UserRole.MANAGER, "• בחר את בית הספר שלך לניהול\n• נהל רכזים והרשאות");
        schoolHelp.put(UserRole.ADMIN, "• בחר בית ספר לניהול\n• הוסף או הסר מנהלים");
        helpContent.put("school_select", schoolHelp);
        
        // Megama management help
        Map<UserRole, String> megamaHelp = new HashMap<>();
        megamaHelp.put(UserRole.GUEST, "• צפה בפרטי המגמה\n• הצג תמונות ותיאור\n• בדוק תנאי קבלה");
        megamaHelp.put(UserRole.RAKAZ, "• הוסף או ערוך מגמות\n• העלה תמונות מהגלריה או צלם\n• עדכן תנאי קבלה ותיאור");
        megamaHelp.put(UserRole.MANAGER, "• צפה במגמות בית הספר\n• נהל הרשאות רכזים\n• אשר שינויים");
        megamaHelp.put(UserRole.ADMIN, "• פקח על כל המגמות במערכת\n• נהל הרשאות בתי ספר");
        helpContent.put("megama", megamaHelp);
    }
    
    /**
     * Show help dialog for current screen
     */
    public static void showHelp(Context context, String screen, UserRole role) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        
        // Inflate custom layout for help dialog
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_help, null);
        TextView titleText = view.findViewById(R.id.helpTitleText);
        TextView contentText = view.findViewById(R.id.helpContentText);
        
        // Set title based on screen
        String title = getHelpTitle(screen);
        titleText.setText(title);
        
        // Get help content for current role
        String content = getHelpContent(screen, role);
        contentText.setText(content);
        
        builder.setView(view)
               .setPositiveButton("הבנתי", null)
               .show();
    }
    
    /**
     * Get help title for screen
     */
    private static String getHelpTitle(String screen) {
        switch (screen) {
            case "login":
                return "עזרה - התחברות למערכת";
            case "school_select":
                return "עזרה - בחירת בית ספר";
            case "megama":
                return "עזרה - ניהול מגמות";
            default:
                return "עזרה";
        }
    }
    
    /**
     * Get help content for screen and role
     */
    private static String getHelpContent(String screen, UserRole role) {
        Map<UserRole, String> screenHelp = helpContent.get(screen);
        if (screenHelp != null) {
            String content = screenHelp.get(role);
            if (content != null) {
                return content;
            }
        }
        return "אין תוכן עזרה זמין לדף זה.";
    }
} 