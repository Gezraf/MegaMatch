package com.project.megamatch;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * מחלקה זו מייצגת את מסך הפתיחה (Splash Screen) של האפליקציה.
 * תפקידה להציג אנימציית פתיחה קצרה עם לוגו ושם האפליקציה,
 * ולאחר מכן לנווט אוטומטית למסך ההתחברות הראשי.
 */
public class SplashActivity extends AppCompatActivity {

    /**
     * משך הזמן המינימלי שמסך הפתיחה יוצג (באלפיות השנייה).
     * הוגדר ל-3000 אלפיות שנייה (3 שניות) לחווית טעינה חלקה.
     */
    private static final int SPLASH_DURATION = 3000;
    /**
     * רכיב ה-ImageView המציג את לוגו האפליקציה.
     */
    private ImageView logoImage;
    /**
     * רכיב ה-TextView המציג את שם האפליקציה.
     */
    private TextView titleText;
    /**
     * רכיב ה-ConstraintLayout הראשי של המסך.
     */
    private ConstraintLayout rootLayout;

    /**
     * נקודת הכניסה לפעילות. מאתחלת את רכיבי הממשק, טוענת ומפעילה אנימציות,
     * ומגדירה השהיה לפני המעבר למסך ההתחברות.
     * @param savedInstanceState אובייקט Bundle המכיל את מצב הפעילות שנשמר.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // קבלת הפניות לרכיבי הממשק עבור האנימציות
        logoImage = findViewById(R.id.logo_image);
        titleText = findViewById(R.id.title_text);
        rootLayout = findViewById(android.R.id.content).getRootView().findViewById(R.id.splash_root);

        try {
            // טעינת אנימציות הלוגו והטקסט
            Animation logoBounceAnim = AnimationUtils.loadAnimation(this, R.anim.fade_and_bounce);
            Animation textSlideAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_and_fade);

            // הפעלת האנימציות
            logoImage.startAnimation(logoBounceAnim);
            titleText.startAnimation(textSlideAnim);
        } catch (Exception e) {
            // במקרה שכשל טעינת או הפעלת אנימציות, עדיין ניתן להמשיך
            e.printStackTrace();
        }

        // הגדרת השהיה לפני הפעלת אנימציית ה-Fade Out והמעבר למסך הבא
        new Handler().postDelayed(this::startFadeOutAndTransition, SPLASH_DURATION);
    }

    /**
     * מפעיל את אנימציית ה-Fade Out עבור הלוגו והטקסט,
     * ולאחר השלמתה מנווט למסך ההתחברות.
     */
    private void startFadeOutAndTransition() {
        try {
            // יצירת אנימציות Fade Out
            ObjectAnimator logoFadeOut = ObjectAnimator.ofFloat(logoImage, "alpha", 1f, 0f);
            ObjectAnimator textFadeOut = ObjectAnimator.ofFloat(titleText, "alpha", 1f, 0f);
            
            // הכנת סט אנימציות שירוץ במקביל
            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(logoFadeOut, textFadeOut);
            animSet.setDuration(900); // הגדלת משך הזמן ל-900 אלפיות שנייה (0.9 שניות) למעבר חלק יותר
            animSet.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)); // אינטרפולטור להאטה הדרגתית

            // הוספת מאזין לאנימציה כדי לנווט כשהיא מסתיימת
            animSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    navigateToLoginScreen();
                }
            });
            
            // הפעלת האנימציה
            animSet.start();
        } catch (Exception e) {
            // אם האנימציה נכשלת, נווט ישירות
            e.printStackTrace();
            navigateToLoginScreen();
        }
    }

    /**
     * מנווט את המשתמש למסך ההתחברות (loginPage) ומסיים את הפעילות הנוכחית.
     */
    private void navigateToLoginScreen() {
        Intent intent = new Intent(SplashActivity.this, loginPage.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
} 