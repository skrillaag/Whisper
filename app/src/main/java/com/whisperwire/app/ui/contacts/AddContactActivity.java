package com.whisperwire.app.ui.contacts;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.whisperwire.app.R;

public class AddContactActivity extends AppCompatActivity {

    // ViewModel coordinating contact request creation via Firestore.
    private ContactsViewModel contactsViewModel;

    // UI elements for entering contact email and sending the request.
    private EditText editContactEmail;
    private Button buttonSendRequest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_contact);

        contactsViewModel = new ViewModelProvider(this).get(ContactsViewModel.class);

        editContactEmail = findViewById(R.id.editContactEmail);
        buttonSendRequest = findViewById(R.id.buttonSendRequest);

        observeViewModel();
        setupClick();
    }

    private void observeViewModel() {
        // Observe send-request result and close screen on success.
        contactsViewModel.getRequestSuccess().observe(this, success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(this, "Request sent", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // Surface repository errors as a toast message.
        contactsViewModel.getRequestErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupClick() {
        buttonSendRequest.setOnClickListener(v -> {
            String email = editContactEmail.getText().toString().trim();
            // Require a non-empty email before hitting the backend.
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Email required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Delegate send logic to ContactsViewModel (ContactsRepository).
            contactsViewModel.sendRequest(email);
        });
    }
}
