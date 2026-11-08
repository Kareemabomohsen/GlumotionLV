package com.example.glumotion.common;

import com.example.glumotion.R;
import com.example.glumotion.chat.ChatBotActivity;
import com.example.glumotion.coach.NotificationsActivity;
import com.example.glumotion.food.FoodActivity;
import com.example.glumotion.glucose.DMActivity;
import com.example.glumotion.health.HealthActivity;
import com.example.glumotion.home.HomePageActivity;
import com.example.glumotion.profile.ProfileActivity;
import com.example.glumotion.profile.SettingsActivity;
import com.example.glumotion.start.LoginActivity;
import com.example.glumotion.workout.WorkoutActivity;

import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

/**
 * Parent class for every main screen (Health, Food, Dashboard, Workout, DM).
 * It holds the shared code of the bottom navigation bar and the top bar,
 * so every screen inherits it instead of copying the same code 5 times (OOP - inheritance).
 */
public class BaseActivity extends AppCompatActivity {

    /**
     * Which bot this page belongs to (DM pages → sugar bot, Workout pages → workout bot...).
     * Every page can override it (polymorphism). null = this page has no bot button.
     */
    protected String getChatMode() {
        return ChatBotActivity.MODE_GENERAL;
    }

    // Every page calls setContentView → here we also connect the bot button of the page header (if it has one)
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        connectHeaderBotButton();
    }

    /**
     * Pages with a 🤖 button in their header (DM → Sugar Bot, Workout → Workout Bot, Medications → GluBot):
     * the button opens the bot of this page. (There is NO floating round chat button anymore.)
     */
    private void connectHeaderBotButton() {
        final String pageMode = getChatMode();
        View headerBot = findViewById(R.id.btnSectionBot);
        if (pageMode == null || headerBot == null) {
            return;
        }
        headerBot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChatBotActivity.open(BaseActivity.this, pageMode);
            }
        });
    }

    // Connects the bottom navigation bar and marks the current tab as selected
    protected void setupBottomNav(int selectedItemId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        if (bottomNav == null) {
            return;
        }

        // Select the current tab BEFORE adding the listener (so it doesn't reopen the same screen)
        bottomNav.setSelectedItemId(selectedItemId);

        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Class<?> target = getTargetScreen(item.getItemId());
                if (target == null) {
                    return false;
                }
                // Already on this screen → do nothing
                if (target == BaseActivity.this.getClass()) {
                    return true;
                }
                onBottomNavClick(target);
                return true;
            }
        });
    }

    // true for inner pages (log glucose, trends...) that also show the bottom bar
    protected boolean isSubPage = false;

    // Called when a tab is pressed. A screen can override it (e.g. to ask "leave the workout?")
    protected void onBottomNavClick(Class<?> target) {
        if (isSubPage) {
            // Inner page → open the tab fresh and close all the pages behind
            Intent intent = new Intent(this, target);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        navigateTo(target);
    }

    // Opens another main screen and closes this one
    protected void navigateTo(Class<?> target) {
        Intent intent = new Intent(this, target);
        startActivity(intent);
        finish();
    }

    // Returns which Activity belongs to each tab of the bottom bar
    private Class<?> getTargetScreen(int itemId) {
        if (itemId == R.id.nav_health) {
            return HealthActivity.class;
        } else if (itemId == R.id.nav_food) {
            return FoodActivity.class;
        } else if (itemId == R.id.nav_dashboard) {
            return HomePageActivity.class;
        } else if (itemId == R.id.nav_workout) {
            return WorkoutActivity.class;
        } else if (itemId == R.id.nav_dm) {
            return DMActivity.class;
        }
        return null;
    }

    // Connects the two buttons of the top bar (chat button + profile menu)
    protected void setupTopBar() {
        View btnChat = findViewById(R.id.btn_top_chat);
        View btnProfile = findViewById(R.id.btn_top_profile);

        if (btnChat != null) {
            btnChat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // The chat button of the top bar always opens the GENERAL bot (GluBot - everything)
                    ChatBotActivity.open(BaseActivity.this, ChatBotActivity.MODE_GENERAL);
                }
            });
        }

        if (btnProfile != null) {
            btnProfile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showProfileMenu(v);
                }
            });
        }
    }

    // Shows the popup menu (Profile / Notifications / Settings / Logout) under the profile icon
    private void showProfileMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.profile_menu, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.menu_profile) {
                    startActivity(new Intent(BaseActivity.this, ProfileActivity.class));
                    return true;
                }
                if (id == R.id.menu_notifications) {
                    startActivity(new Intent(BaseActivity.this, NotificationsActivity.class));
                    return true;
                }
                if (id == R.id.menu_settings) {
                    startActivity(new Intent(BaseActivity.this, SettingsActivity.class));
                    return true;
                }
                if (id == R.id.menu_logout) {
                    logout();
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    /**
     * For pages that are not in the bottom bar: shows the title and makes the
     * back arrow (←) close the page. The layout must include @layout/toolbar_back
     */
    protected void setupBackBar(String title) {
        TextView tvTitle = findViewById(R.id.tvToolbarTitle);
        if (tvTitle != null) {
            tvTitle.setText(title);
        }
        View btnBack = findViewById(R.id.btnToolbarBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Same as the phone's back button (so pages that ask "are you sure?" still ask)
                    getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }
    }

    // Shows a text button on the right of the back bar (e.g. "Save")
    protected TextView setupToolbarAction(String text, View.OnClickListener listener) {
        TextView action = findViewById(R.id.btnToolbarAction);
        if (action != null) {
            action.setText(text);
            action.setVisibility(View.VISIBLE);
            action.setOnClickListener(listener);
        }
        return action;
    }

    // Short Toast message (used by many screens)
    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Signs the user out of Firebase and clears all screens so Back can't return
    protected void logout() {
        FRef.getAuth().signOut();
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
