package com.whisperwire.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.whisperwire.app.ui.main.MainActivity;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.whisperwire.app.R;

/**
 * Login screen activity.
 * Handles user login, navigation to registration,
 * and password reset requests.
 */
public class AuthLoginActivity extends AppCompatActivity {

    // ViewModel responsible for Firebase Auth logic and error state.
    private AuthViewModel authViewModel;

    // UI fields
    private EditText editEmail;
    private EditText editPassword;
    private Button buttonLogin;
    private Button buttonRegister;
    private ProgressBar progressLogin;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_login);

        // Initialize ViewModel and UI components
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);
        progressLogin = findViewById(R.id.progressLogin);

        observeViewModel();
        setupClickListeners();
    }

    /**
     * Observes login, registration, error, and loading LiveData from AuthViewModel.
     * Displays Toast messages and toggles loading spinner.
     */
    private void observeViewModel() {
        authViewModel.getLoginSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                // On successful login I move to the main E2EE UI and clear the back stack.
                Toast.makeText(this, "Login success", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else if (Boolean.FALSE.equals(success)) {
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        });

        authViewModel.getRegisterSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                // Registration succeeded; user can now log in using the same credentials.
                Toast.makeText(this, "Register success", Toast.LENGTH_SHORT).show();
            } else if (Boolean.FALSE.equals(success)) {
                Toast.makeText(this, "Register failed", Toast.LENGTH_SHORT).show();
            }
        });

        authViewModel.getAuthErrorMessage().observe(this, error -> {
            if (error != null) {
                // Surface backend or validation errors as a simple toast.
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });

        authViewModel.getLoading().observe(this, isLoading -> {
            boolean loading = Boolean.TRUE.equals(isLoading);
            progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);

            // I disable interaction while Firebase authentication is in progress to avoid duplicate submissions.
            editEmail.setEnabled(!loading);
            editPassword.setEnabled(!loading);
            buttonLogin.setEnabled(!loading);
            buttonRegister.setEnabled(!loading);
        });
    }

    /**
     * Sets up click listeners for login, register, and forgot password.
     */
    private void setupClickListeners() {
        buttonLogin.setOnClickListener(v -> {
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();

            // Basic client-side validation before hitting Firebase Auth.
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(this, "Email and password required", Toast.LENGTH_SHORT).show();
                return;
            }

            authViewModel.login(email, password);
        });

        buttonRegister.setOnClickListener(v -> {
            // Navigate to registration screen; auth flow continues there.
            Intent intent = new Intent(this, AuthRegisterActivity.class);
            startActivity(intent);
        });

        TextView textForgot = findViewById(R.id.textForgot);
        textForgot.setOnClickListener(v -> {
            String email = editEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                // I require an email so Firebase can send a reset link to a known account.
                Toast.makeText(this, "Enter your email to reset password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Delegates password reset handling to Firebase Auth backend.
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Reset email sent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to send reset email", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}
