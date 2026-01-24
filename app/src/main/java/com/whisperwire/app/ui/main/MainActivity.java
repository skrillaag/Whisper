package com.whisperwire.app.ui.main;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.whisperwire.app.R;
import com.whisperwire.app.data.AccountDeletionRepository;
import com.whisperwire.app.data.chat.ChatSummary;
import com.whisperwire.app.ui.auth.AuthLoginActivity;
import com.whisperwire.app.ui.chat.ChatActivity;
import com.whisperwire.app.ui.contacts.ContactsListActivity;
import com.whisperwire.app.ui.contacts.PendingRequestsViewModel;
import com.whisperwire.app.ui.dev.DeveloperActivity;

/**
 * Main screen shown after user login.
 *
 * Provides:
 *  - List of recent chats (with unread badge behavior)
 *  - Navigation to Contacts screen
 *  - Logout
 *  - Options menu: Developer Mode + Account deletion
 *
 * Observes:
 *  - RecentChatsViewModel → live chat previews
 *  - PendingRequestsViewModel → "●" dot showing pending contact requests
 */
public class MainActivity extends AppCompatActivity {

    private RecentChatsViewModel recentChatsViewModel;
    private RecentChatsAdapter recentChatsAdapter;
    private PendingRequestsViewModel pendingRequestsViewModel;

    private RecyclerView recyclerRecentChats;
    private Button buttonContacts;
    private Button buttonLogout;
    private Toolbar toolbarMain;

    // Handles Firestore + Auth cleanup for full account deletion
    private AccountDeletionRepository accountDeletionRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountDeletionRepository = new AccountDeletionRepository();

        initViews();
        setSupportActionBar(toolbarMain);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Whisper");
        }

        setupRecycler();
        setupBottomMenu();

        // ViewModels powering the main UI
        recentChatsViewModel = new ViewModelProvider(this).get(RecentChatsViewModel.class);
        pendingRequestsViewModel = new ViewModelProvider(this).get(PendingRequestsViewModel.class);

        observeViewModels();

        // Begin Firestore listener for recent chats
        recentChatsViewModel.start();
    }

    private void initViews() {
        toolbarMain = findViewById(R.id.toolbarMain);
        recyclerRecentChats = findViewById(R.id.recyclerRecentChats);
        buttonContacts = findViewById(R.id.buttonContacts);
        buttonLogout = findViewById(R.id.buttonLogout);
    }

    /**
     * Sets up recent chats list:
     * - Reverse chronological order
     * - Clicking a chat opens ChatActivity
     */
    private void setupRecycler() {
        recyclerRecentChats.setLayoutManager(new LinearLayoutManager(this));

        recentChatsAdapter = new RecentChatsAdapter((ChatSummary chat) -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_UID, chat.getContactUid());
            intent.putExtra(ChatActivity.EXTRA_CONTACT_EMAIL, chat.getContactEmail());
            startActivity(intent);
        });

        recyclerRecentChats.setAdapter(recentChatsAdapter);
    }

    /**
     * Bottom actions:
     * - Contacts list
     * - Logout (clear session)
     */
    private void setupBottomMenu() {
        buttonContacts.setOnClickListener(v ->
                startActivity(new Intent(this, ContactsListActivity.class)));

        buttonLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(this, AuthLoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Observes the two ViewModels driving the home screen.
     */
    private void observeViewModels() {

        // Updates chat list (preview + unread state)
        recentChatsViewModel.getChats().observe(this, list -> {
            if (list != null) {
                recentChatsAdapter.setItems(list);
            }
        });

        // Surface Firestore/crypto errors from RecentChatsRepository
        recentChatsViewModel.getError().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Show dot on Contacts button if there are pending incoming requests
        pendingRequestsViewModel.getHasPending().observe(this, hasPending -> {
            if (Boolean.TRUE.equals(hasPending)) {
                buttonContacts.setText("Contacts ●");
            } else {
                buttonContacts.setText("Contacts");
            }
        });
    }

    // ======= OPTIONS MENU (3 DOTS) =======

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main_options, menu);
        return true;
    }

    /**
     * Handles "Developer mode" and "Delete account"
     * from the top-right overflow menu.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_developer) {
            openDeveloperMode();
            return true;
        } else if (id == R.id.action_delete_account) {
            confirmDeleteAccount();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void openDeveloperMode() {
        Intent intent = new Intent(this, DeveloperActivity.class);
        startActivity(intent);
    }

    /**
     * Shows irreversible deletion confirmation dialog.
     * If accepted → deletes:
     *   - /contact_requests involving user
     *   - /users/{uid}/contacts/*
     *   - /users/{uid}/chats/*
     *   - /chats/* where user is participant
     *   - /users/{uid}
     *   - FirebaseAuth account
     */
    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete account")
                .setMessage("This will permanently delete your account, contacts, and chats. This action cannot be undone. Continue?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (DialogInterface dialog, int which) -> performAccountDeletion())
                .show();
    }

    private void performAccountDeletion() {
        Toast.makeText(this, "Deleting account...", Toast.LENGTH_SHORT).show();

        accountDeletionRepository.deleteCurrentUserAndData()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account deleted", Toast.LENGTH_LONG).show();

                        // Return user to login screen
                        Intent intent = new Intent(this, AuthLoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        // Show a readable error
                        String message = "Failed to delete account";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            message = task.getException().getMessage();
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
