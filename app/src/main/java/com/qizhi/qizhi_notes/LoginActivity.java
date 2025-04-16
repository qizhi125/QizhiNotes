package com.qizhi.qizhi_notes;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.qizhi.qizhi_notes.db.MyDbHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final String PREFS_NAME = "UserPrefs";
    private static final String PREF_USERNAME = "username";
    private static final String PREF_PASSWORD = "password"; // INSECURE - Store hash/token instead
    private static final String PREF_REMEMBER_ME = "rememberMe";


    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private CheckBox cbRememberMe;
    private TextView tvGoToRegister; // Link to RegisterActivity
    private ProgressBar progressBar; // Progress indicator

    private MyDbHelper dbHelper;
    private SharedPreferences sharedPreferences;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Theme handles background now, no need to set it here
        setContentView(R.layout.activity_login);

        dbHelper = new MyDbHelper(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        tvGoToRegister = findViewById(R.id.tvGoToRegister);
        progressBar = findViewById(R.id.login_progress); // Make sure ID matches layout

        setupRememberMe();

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvGoToRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void setupRememberMe() {
        boolean rememberMe = sharedPreferences.getBoolean(PREF_REMEMBER_ME, false);
        cbRememberMe.setChecked(rememberMe);

        if (rememberMe) {
            String savedUsername = sharedPreferences.getString(PREF_USERNAME, null);
            String savedPassword = sharedPreferences.getString(PREF_PASSWORD, null); // Retrieving plain password (INSECURE)

            if (savedUsername != null && savedPassword != null) {
                etUsername.setText(savedUsername);
                etPassword.setText(savedPassword);
                // Optional: You could automatically attempt login here if desired
                // attemptLogin();
            } else {
                // If credentials aren't saved properly, uncheck remember me
                cbRememberMe.setChecked(false);
                clearSavedCredentials(); // Clear potentially partial/invalid saved data
            }
        }
    }


    private void attemptLogin() {
        // Reset errors.
        etUsername.setError(null);
        etPassword.setError(null);

        // Store values at the time of the login attempt.
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.error_field_required));
            focusView = etPassword;
            cancel = true;
        } else if (password.length() < 4) { // Basic check
            etPassword.setError(getString(R.string.error_invalid_password_short));
            focusView = etPassword;
            cancel = true;
        }


        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            etUsername.setError(getString(R.string.error_field_required));
            focusView = etUsername;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            if (focusView != null) {
                focusView.requestFocus();
            }
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            checkLoginInBackground(username, password);
        }
    }

    private void checkLoginInBackground(final String username, final String password) {
        executorService.execute(() -> {
            // Perform check in background
            // --- IMPORTANT SECURITY NOTE ---
            // In a real application, retrieve the HASHED password for the user from DB
            // and compare it with the HASH of the entered password.
            final boolean isValidUser = dbHelper.checkUser(username, password);

            mainHandler.post(() -> {
                // Update UI on main thread
                showProgress(false);
                if (isValidUser) {
                    handleLoginSuccess(username, password);
                } else {
                    etPassword.setError(getString(R.string.error_incorrect_password));
                    etPassword.requestFocus();
                    Toast.makeText(LoginActivity.this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void handleLoginSuccess(String username, String password) {
        Toast.makeText(LoginActivity.this, R.string.login_successful, Toast.LENGTH_SHORT).show();

        // Handle "Remember Me"
        if (cbRememberMe.isChecked()) {
            saveCredentials(username, password); // Saving plain password (INSECURE)
        } else {
            clearSavedCredentials();
        }

        // Navigate to MainActivity
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish(); // Finish LoginActivity so user can't go back to it
    }

    private void saveCredentials(String username, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_REMEMBER_ME, true);
        editor.putString(PREF_USERNAME, username);
        editor.putString(PREF_PASSWORD, password); // INSECURE: Avoid saving plain passwords!
        editor.apply();
        Log.d(TAG,"Credentials saved for user: " + username);
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(PREF_USERNAME);
        editor.remove(PREF_PASSWORD);
        editor.putBoolean(PREF_REMEMBER_ME, false);
        editor.apply();
        Log.d(TAG,"Saved credentials cleared.");
    }


    // Shows the progress UI and hides the login form.
    private void showProgress(final boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // Disable/Enable form fields during progress
        etUsername.setEnabled(!show);
        etPassword.setEnabled(!show);
        btnLogin.setEnabled(!show);
        cbRememberMe.setEnabled(!show);
        tvGoToRegister.setEnabled(!show);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shutdown executor when activity is destroyed
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        // Close the database helper
        if (dbHelper != null) {
            dbHelper.close(); // Important: Close DB helper instance
        }
    }
}