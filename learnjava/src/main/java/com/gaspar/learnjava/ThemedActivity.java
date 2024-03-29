package com.gaspar.learnjava;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.gaspar.learnjava.utils.LogUtils;
import com.gaspar.learnjava.utils.ThemeUtils;

/**
 * The base activity for this application. It supports swapping themes at runtime.
 */
public class ThemedActivity extends AppCompatActivity {

    /**
     * Stores the latest theme id this activity is aware of.
     */
    @StyleRes
    private int themeId;

    /**
     * Component of the drawer. Needed for toolbar recoloring.
     */
    protected ActionBarDrawerToggle toggle;

    /**
     * Toolbar of the activity, needed for recoloring.
     */
    protected Toolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtils.initSelectedTheme(this); //initialize themes. wont do anything if already initialized
        themeId = ThemeUtils.getThemeStyleRes(); //save current theme
        setTheme(themeId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int newTheme = ThemeUtils.getThemeStyleRes();
        if(themeId != newTheme) { //theme was updated while activity was hidden
            recreate();
        }
        recolorToolbar();

        //check for "keep awake" status update
        boolean keepAwake = SettingsActivity.keepAwakeEnabled(this);
        if(keepAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else { //turn it off
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /**
     * Updates the colors of the toolbar, if there is one. For some reason this must be done
     * programmatically.
     */
    private void recolorToolbar() {
        if(toolbar != null) {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, ThemeUtils.getPrimaryColor()));
            toolbar.setTitleTextColor(ContextCompat.getColor(this, ThemeUtils.getTextColor()));
            ImageButton settingsIcon = toolbar.findViewById(R.id.settings_icon);
            if(settingsIcon != null) {
                settingsIcon.setImageTintList(ThemeUtils.getImageButtonTintList(this));
                settingsIcon.setBackgroundTintList(ThemeUtils.getImageButtonBackgroundTintList(this));
            }
            if(toggle != null) {
                toggle.getDrawerArrowDrawable().setColor(ContextCompat.getColor(this, ThemeUtils.getTextColor()));
            } else { //no drawer, there is a back arrow
                Drawable arrow = toolbar.getNavigationIcon();
                if(arrow != null) arrow.setTint(ContextCompat.getColor(this, ThemeUtils.getTextColor()));
            }
        } else {
            LogUtils.logError("Toolbar was null, and could not be recolored!");
        }
    }
}
