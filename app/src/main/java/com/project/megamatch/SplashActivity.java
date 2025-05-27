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

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // Adjusted for slower fade-in
    private ImageView logoImage;
    private TextView titleText;
    private ConstraintLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Get views for animation
        logoImage = findViewById(R.id.logo_image);
        titleText = findViewById(R.id.title_text);
        rootLayout = findViewById(android.R.id.content).getRootView().findViewById(R.id.splash_root);

        try {
            // Load animations
            Animation logoBounceAnim = AnimationUtils.loadAnimation(this, R.anim.fade_and_bounce);
            Animation textSlideAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_and_fade);

            // Start animations
            logoImage.startAnimation(logoBounceAnim);
            titleText.startAnimation(textSlideAnim);
        } catch (Exception e) {
            // If animations fail, we can still proceed
            e.printStackTrace();
        }

        // Delay and then go to next screen with fade out
        new Handler().postDelayed(this::startFadeOutAndTransition, SPLASH_DURATION);
    }

    private void startFadeOutAndTransition() {
        try {
            // Create fade out animations
            ObjectAnimator logoFadeOut = ObjectAnimator.ofFloat(logoImage, "alpha", 1f, 0f);
            ObjectAnimator textFadeOut = ObjectAnimator.ofFloat(titleText, "alpha", 1f, 0f);
            
            // Prepare the animation set
            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(logoFadeOut, textFadeOut);
            animSet.setDuration(900); // Increased to 900ms (0.9 seconds) for a smoother fade-out
            animSet.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)); // Smoother deceleration
            
            // Add listener to navigate when animation completes
            animSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    navigateToLoginScreen();
                }
            });
            
            // Start the animation
            animSet.start();
        } catch (Exception e) {
            // If animation fails, navigate directly
            e.printStackTrace();
            navigateToLoginScreen();
        }
    }

    private void navigateToLoginScreen() {
        Intent intent = new Intent(SplashActivity.this, loginPage.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
} 