// הקובץ הזה משומש לאחסון מבני נתונים שמנתחים את קובץ מסד הנתונים של בתי הספר בכלל הארץ

package com.project.megamatch;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * מחלקה זו משמשת לאחסון וטעינה של נתוני בתי ספר מקובץ CSV.
 * היא מספקת שיטות לטעינת בתי ספר מתוך קובץ `schools.csv`,
 * ולאחזור פרטי בתי ספר לפי מזהה או קבלת רשימה של כל בתי הספר.
 */
public class schoolsDB {

    /**
     * מפה (HashMap) המאחסנת אובייקטי School, כאשר מזהה בית הספר הוא המפתח.
     * משמשת לגישה מהירה לפרטי בית ספר לפי המזהה שלו.
     */
    private static final HashMap<Integer, School> schoolsMap = new HashMap<>();

    /**
     * טוענת את נתוני בתי הספר מקובץ ה-CSV המובנה ביישום (`R.raw.schools`).
     * שיטה זו מנקה את המפה הקיימת של בתי הספר ומוסיפה אליה את הנתונים החדשים מהקובץ.
     * @param context הקונטקסט של היישום, המשמש לגישה למשאבי הקובץ הגולמי.
     */
    public static void loadSchoolsFromCSV(Context context) {
        schoolsMap.clear();

        try (InputStream is = context.getResources().openRawResource(R.raw.schools);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // ניתוח שורת ה-CSV תוך טיפול נכון בגרשיים
                String[] columns = parseCSVLine(line);

                if (columns.length < 3) continue;

                String schoolIdStr = columns[0].replace("\uFEFF", "").trim();

                try {
                    int schoolId = Integer.parseInt(schoolIdStr);
                    String schoolName = columns[1].trim();
                    String town = columns[2].trim(); // שונה מ-managerName ל-town לפי התיקון הקודם של המשתמש

                    schoolsMap.put(schoolId, new School(schoolName, town));

                } catch (NumberFormatException ignored) {}
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * מנתחת שורת טקסט מקובץ CSV תוך טיפול נכון בגרשיים (quotes).
     * @param line שורת הטקסט לניתוח.
     * @return מערך של מחרוזות, כאשר כל מחרוזת מייצגת שדה בודד מהשורה.
     */
    private static String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                // אם אנו רואים גרש ואנו כבר בתוך גרשיים, בדוק אם זהו גרש "מוברח" (escaped quote)
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"'); // הוסף גרש בודד
                    i++; // דלג על הגרש הבא
                } else {
                    // שנה את מצב הגרשיים
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // סוף שדה
                result.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        
        // הוסף את השדה האחרון
        result.add(field.toString());
        
        return result.toArray(new String[0]);
    }

    /**
     * מאחזר את פרטי בית הספר לפי מזהה בית הספר.
     * @param schoolId מזהה בית הספר המבוקש.
     * @return אובייקט School המייצג את בית הספר, או null אם לא נמצא בית ספר עם המזהה הנתון.
     */
    public static School getSchoolById(int schoolId) {
        return schoolsMap.get(schoolId);
    }

    /**
     * מחזירה את המספר הכולל של בתי הספר שנטענו במסד הנתונים.
     * @return המספר הכולל של בתי הספר.
     */
    public static int getTotalSchoolsCount()
    {
        return schoolsMap.size();
    }

    /**
     * מחזירה את מפת בתי הספר המלאה (HashMap) המכילה את כל בתי הספר שנטענו.
     * @return מפה של בתי ספר, כאשר המפתח הוא מזהה בית הספר והערך הוא אובייקט School.
     */
    public static HashMap<Integer, School> getSchoolsMap() {
        return schoolsMap;
    }

    /**
     * מחזירה רשימה של כל בתי הספר שנטענו, כולל מזהה בית הספר לכל אחד.
     * @return רשימה של אובייקטי School.
     */
    public static List<School> getAllSchools() {
        List<School> schoolsList = new ArrayList<>();
        for (Map.Entry<Integer, School> entry : schoolsMap.entrySet()) {
            School school = entry.getValue();
            // צור אובייקט School עם המזהה
            School schoolWithId = new School(school.getSchoolName(), school.getTown()); // שונה ל-town
            schoolWithId.setSchoolId(entry.getKey());
            schoolsList.add(schoolWithId);
        }
        return schoolsList;
    }

    /**
     * מחלקה פנימית המייצגת אובייקט בית ספר, עם פרטים כמו שם, עיר ומזהה.
     */
    public static class School {
        /**
         * שם בית הספר.
         */
        private final String schoolName;
        /**
         * עיר בית הספר. (הוחלף מ-managerName ל-town)
         */
        private final String town;
        /**
         * מזהה בית הספר (סמל מוסד).
         */
        private int schoolId;

        /**
         * בונה חדש עבור אובייקט School.
         * @param schoolName שם בית הספר.
         * @param town עיר בית הספר. (הוחלף מ-managerName ל-town)
         */
        public School(String schoolName, String town) {
            this.schoolName = schoolName;
            this.town = town;
        }

        /**
         * מחזירה את שם בית הספר.
         * @return שם בית הספר.
         */
        public String getSchoolName() {
            return schoolName;
        }

        /**
         * מחזירה את עיר בית הספר.
         * @return עיר בית הספר. (הוחלף מ-getManagerName ל-getTown)
         */
        public String getTown() {
            return town;
        }
        
        /**
         * מגדירה את מזהה בית הספר.
         * @param schoolId מזהה בית הספר.
         */
        public void setSchoolId(int schoolId) {
            this.schoolId = schoolId;
        }
        
        /**
         * מחזירה את מזהה בית הספר.
         * @return מזהה בית הספר.
         */
        public int getSchoolId() {
            return schoolId;
        }
        
        /**
         * מחזירה ייצוג מחרוזתי של אובייקט School, שהוא שם בית הספר.
         * @return שם בית הספר.
         */
        @Override
        public String toString() {
            return schoolName;
        }
    }
}
