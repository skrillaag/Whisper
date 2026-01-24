package com.whisperwire.app.ui.chat;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.whisperwire.app.R;
import com.whisperwire.app.util.SafetyNumberUtil;

public class ChatSecurityActivity extends AppCompatActivity {

    // UI elements showing contact identity and computed safety number.
    private TextView textContactEmail;
    private TextView textSafetyNumber;

    // Contact identity passed from ChatActivity.
    private String contactUid;
    private String contactEmail;

    // Firestore and Auth for loading both users' identity keys.
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_security);

        textContactEmail = findViewById(R.id.textContactEmail);
        textSafetyNumber = findViewById(R.id.textSafetyNumber);

        contactUid = getIntent().getStringExtra(ChatActivity.EXTRA_CONTACT_UID);
        contactEmail = getIntent().getStringExtra(ChatActivity.EXTRA_CONTACT_EMAIL);

        if (contactUid == null || contactEmail == null) {
            // Without both UID and email the verification context is incomplete.
            Toast.makeText(this, "Missing contact info", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        textContactEmail.setText(contactEmail);

        // Start loading the keys needed to compute the safety number.
        loadSafetyNumber();
    }

    private void loadSafetyNumber() {
        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            // If not logged in I cannot trust or compute any identity binding.
            textSafetyNumber.setText("Not logged in");
            return;
        }

        String myUid = me.getUid();

        // Load my user doc
        firestore.collection("users")
                .document(myUid)
                .get()
                .addOnSuccessListener(myDoc -> {
                    // Then load contact doc
                    firestore.collection("users")
                            .document(contactUid)
                            .get()
                            .addOnSuccessListener(contactDoc -> handleDocs(myUid, myDoc, contactDoc))
                            .addOnFailureListener(e ->
                                    textSafetyNumber.setText("Failed to load contact key"));
                })
                .addOnFailureListener(e ->
                        textSafetyNumber.setText("Failed to load my key"));
    }

    private void handleDocs(String myUid, DocumentSnapshot myDoc, DocumentSnapshot contactDoc) {
        // Both user documents must be present to verify identity keys.
        if (myDoc == null || !myDoc.exists() || contactDoc == null || !contactDoc.exists()) {
            textSafetyNumber.setText("Missing user data");
            return;
        }

        // Identity public keys stored in users/{uid}.identityPublicKey (Base64-encoded EC keys).
        String myKey = myDoc.getString("identityPublicKey");
        String theirKey = contactDoc.getString("identityPublicKey");

        if (myKey == null || theirKey == null) {
            // If any key is missing the safety number cannot be computed.
            textSafetyNumber.setText("Missing keys");
            return;
        }

        // Safety number deterministically binds both UIDs and identity keys for manual verification.
        String safetyNumber = SafetyNumberUtil.computeSafetyNumber(myUid, contactUid, myKey, theirKey);
        textSafetyNumber.setText(safetyNumber);
    }
}
