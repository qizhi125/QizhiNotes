package com.qizhi.qizhi_notes;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar; // Import ProgressBar


import com.qizhi.qizhi_notes.db.MyDbHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private EditText etUsername;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private ProgressBar progressBar; // Added ProgressBar
    private MyDbHelper dbHelper;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(); // For background tasks
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // To post results to main thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Theme handles background now
        setContentView(R.layout.activity_register);

        dbHelper = new MyDbHelper(this);

        etUsername = findViewById(R.id.etRegisterUsername);
        etPassword = findViewById(R.id.etRegisterPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar = findViewById(R.id.register_progress); // Find ProgressBar

        btnRegister.setOnClickListener(v -> attemptRegister());

        tvBackToLogin.setOnClickListener(v -> {
            // Simply finish this activity to go back to LoginActivity
            finish();
        });
    }

    private void attemptRegister() {
        // Reset errors.
        etUsername.setError(null);
        etPassword.setError(null);
        etConfirmPassword.setError(null);

        // Store values at the time of the login attempt.
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid confirm password.
        if (TextUtils.isEmpty(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_field_required));
            focusView = etConfirmPassword;
            cancel = true;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_password_mismatch));
            focusView = etConfirmPassword;
            cancel = true;
        }

        // Check for a valid password.
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.error_field_required));
            focusView = etPassword;
            cancel = true;
        } else if (password.length() < 4) { // Basic password length check
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
            // There was an error; don't attempt registration and focus the first
            // form field with an error.
            if (focusView != null) {
                focusView.requestFocus();
            }
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user registration attempt.
            showProgress(true);
            registerUserInBackground(username, password);
        }
    }

    private void registerUserInBackground(final String username, final String password) {
        executorService.execute(() -> {
            // Perform check and registration in background
            final boolean usernameExists = dbHelper.checkUsernameExists(username);
            long result = -1; // Default to error

            if (usernameExists) {
                result = -2; // Special code for username exists
            } else {
                // --- IMPORTANT SECURITY NOTE ---
                // In a real application, DO NOT store plain text passwords.
                // HASH the password here using a strong algorithm like Argon2, scrypt, or bcrypt
                // before passing it to dbHelper.addUser().
                // Example (conceptual): String hashedPassword = hashPassword(password);
                // result = dbHelper.addUser(username, hashedPassword);
                // For this example, we store plain text (INSECURE)
                result = dbHelper.addUser(username, password);
            }

            final long finalResult = result;
            mainHandler.post(() -> {
                // Update UI on the main thread
                showProgress(false); // Hide progress
                if (finalResult >= 0) { // Success (new user ID is 0 or greater)
                    Toast.makeText(RegisterActivity.this, R.string.registration_successful, Toast.LENGTH_SHORT).show();
                    // Registration successful, finish activity to return to Login
                    finish();
                } else if (finalResult == -2) { // Username already exists
                    etUsername.setError(getString(R.string.error_username_exists));
                    etUsername.requestFocus();
                    Toast.makeText(RegisterActivity.this, R.string.error_username_exists, Toast.LENGTH_SHORT).show();
                } else { // Other DB error
                    Toast.makeText(RegisterActivity.this, R.string.registration_failed, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Registration failed for user: " + username + " with code: " + finalResult);
                }
            });
        });
    }

    // Method to show/hide a ProgressBar
    private void showProgress(final boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        // Also, disable/enable input fields and button while progressing
        etUsername.setEnabled(!show);
        etPassword.setEnabled(!show);
        etConfirmPassword.setEnabled(!show);
        btnRegister.setEnabled(!show);
        tvBackToLogin.setEnabled(!show); // Disable back link during operation
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
            dbHelper.close();
        }
    }
}