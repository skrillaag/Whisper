package com.whisperwire.app.ui.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.whisperwire.app.R;
import com.whisperwire.app.data.contacts.Contact;
import com.whisperwire.app.ui.chat.ChatActivity;

public class ContactsListActivity extends AppCompatActivity {

    private ContactsViewModel contactsViewModel;

    private RecyclerView recyclerContacts;
    private ContactsAdapter adapter;

    private Button buttonAddContact;
    private Button buttonContactRequests;

    // Firebase instances used only for UI-level listeners (non-sensitive)
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    // Listener that tracks pending incoming contact requests
    private ListenerRegistration requestsRegistration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts_list);

        contactsViewModel = new ViewModelProvider(this).get(ContactsViewModel.class);

        recyclerContacts = findViewById(R.id.recyclerContacts);
        recyclerContacts.setLayoutManager(new LinearLayoutManager(this));

        buttonAddContact = findViewById(R.id.buttonAddContact);
        buttonContactRequests = findViewById(R.id.buttonContactRequests);

        // Adapter emits contact click events → opens ChatActivity
        adapter = new ContactsAdapter((Contact contact) -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_UID, contact.getContactUid());
            intent.putExtra(ChatActivity.EXTRA_CONTACT_EMAIL, contact.getContactEmail());
            startActivity(intent);
        });

        recyclerContacts.setAdapter(adapter);

        setupClicks();
        observeViewModel();

        // Live listener for pending request count (unread-dot UI)
        startRequestsListener();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh contact list from Firestore on activity return
        contactsViewModel.loadContacts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the snapshot listener to prevent memory leaks
        if (requestsRegistration != null) {
            requestsRegistration.remove();
            requestsRegistration = null;
        }
    }

    private void setupClicks() {
        // Navigate to "Add Contact" form
        buttonAddContact.setOnClickListener(v ->
                startActivity(new Intent(this, AddContactActivity.class)));

        // Navigate to activity showing pending requests
        buttonContactRequests.setOnClickListener(v ->
                startActivity(new Intent(this, ContactRequestsActivity.class)));
    }

    private void observeViewModel() {
        // Live list of accepted contacts
        contactsViewModel.getContacts().observe(this, contacts -> adapter.setItems(contacts));

        // Surface errors related to Firestore contact loading
        contactsViewModel.getRequestErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Local Firestore listener for pending contact requests.
     * Updates UI badge: a " ●" appended to the button text.
     *
     * Firestore path:
     * - /contact_requests where toUid = currentUser and status = "pending"
     */
    private void startRequestsListener() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            // No user → no incoming requests
            updatePendingDot(false);
            return;
        }

        String myUid = current.getUid();

        if (requestsRegistration != null) {
            // Ensure only one listener is active at a time
            requestsRegistration.remove();
            requestsRegistration = null;
        }

        requestsRegistration = firestore.collection("contact_requests")
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snap, error) -> {
                    // Non-critical UI indicator; on error hide the dot
                    boolean hasPending = error == null && snap != null && !snap.isEmpty();
                    updatePendingDot(hasPending);
                });
    }

    /**
     * Updates button label to include or remove unread indicator.
     */
    private void updatePendingDot(boolean hasPending) {
        String baseText = "Contact Requests"; // UI text only
        String dot = hasPending ? " \u25CF" : ""; // " ●" indicator
        buttonContactRequests.setText(baseText + dot);
    }
}
