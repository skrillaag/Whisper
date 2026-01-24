package com.whisperwire.app.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.whisperwire.app.R;
/**
 * Registration screen activity.
 * Allows user to create a new account by entering email,
 * password, and confirming the password.
 */

public class AuthRegisterActivity extends AppCompatActivity {

    // ViewModel handling FirebaseAuth operations and error reporting.
    private AuthViewModel authViewModel;

    // UI fields for registration input
    private EditText editEmail;
    private EditText editPassword;
    private EditText editConfirmPassword;
    private Button buttonFinishRegister;

    // Initialize ViewModel and UI references
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_register);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        editConfirmPassword = findViewById(R.id.editConfirmPassword);
        buttonFinishRegister = findViewById(R.id.buttonFinishRegister);

        observeViewModel();
        setupClick();
    }

    /**
     * Observes registration result and error messages from ViewModel.
     * I surface results as Toasts and close the screen on success.
     */
    private void observeViewModel() {
        authViewModel.getRegisterSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show();
                finish(); // go back to Login screen
            } else if (Boolean.FALSE.equals(success)) {
                Toast.makeText(this, "Register failed", Toast.LENGTH_SHORT).show();
            }
        });

        authViewModel.getAuthErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Handles form validation and performs registration.
     * I validate locally before delegating to Firebase via AuthViewModel.
     */
    private void setupClick() {
        buttonFinishRegister.setOnClickListener(v -> {
            String email = editEmail.getText().toString().trim();
            String pass = editPassword.getText().toString().trim();
            String confirm = editConfirmPassword.getText().toString().trim();

            // Basic input validation
            if (TextUtils.isEmpty(email) ||
                    TextUtils.isEmpty(pass) ||
                    TextUtils.isEmpty(confirm)) {

                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Password confirmation check
            if (!pass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            // Trigger ViewModel registration
            authViewModel.register(email, pass);
        });
    }
}
