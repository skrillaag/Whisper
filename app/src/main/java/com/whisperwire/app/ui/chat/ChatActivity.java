package com.whisperwire.app.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.chat.ChatMessage;

import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONTACT_UID = "extra_contact_uid";
    public static final String EXTRA_CONTACT_EMAIL = "extra_contact_email";

    // ViewModel coordinating encrypted chat logic and Firestore listeners.
    private ChatViewModel vm;

    // UI widgets for chat screen.
    private Toolbar toolbar;
    private RecyclerView recycler;
    private EditText edit;
    private ImageButton send;
    private TextView textTypingIndicator;

    // Adapter for rendering decrypted ChatMessage objects.
    private ChatMessagesAdapter adapter;

    // Contact identity passed from previous screen.
    private String contactUid;
    private String contactEmail;

    // Tracks local typing state to avoid redundant typing updates.
    private boolean isTypingLocal = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        contactUid = getIntent().getStringExtra(EXTRA_CONTACT_UID);
        contactEmail = getIntent().getStringExtra(EXTRA_CONTACT_EMAIL);

        if (contactUid == null || contactEmail == null) {
            // Without a valid contact I cannot establish a chat context.
            Toast.makeText(this, "Missing contact info", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        vm = new ViewModelProvider(this).get(ChatViewModel.class);

        toolbar = findViewById(R.id.toolbarChat);
        recycler = findViewById(R.id.recyclerMessages);
        edit = findViewById(R.id.editMessage);
        send = findViewById(R.id.buttonSend);
        textTypingIndicator = findViewById(R.id.textTypingIndicator);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            // I show the contact email as the chat title for clarity.
            getSupportActionBar().setTitle(contactEmail);
        }

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);

        adapter = new ChatMessagesAdapter();
        recycler.setAdapter(adapter);

        // Initially disable input until chat and crypto pipeline are ready.
        send.setEnabled(false);
        edit.setEnabled(false);

        observe();
        setupInput();

        // Kick off ViewModel initialization (chat id, listeners, keys, etc.).
        vm.init(contactUid);
    }

    private void observe() {
        vm.getMessages().observe(this, this::update);

        vm.getError().observe(this, msg -> {
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });

        vm.getDeleteSuccess().observe(this, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        vm.getDeleteError().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        vm.getRemoteTyping().observe(this, typing -> {
            // Simple typing indicator when the remote side is active.
            if (Boolean.TRUE.equals(typing)) {
                textTypingIndicator.setVisibility(View.VISIBLE);
            } else {
                textTypingIndicator.setVisibility(View.GONE);
            }
        });

        vm.getChatReady().observe(this, ready -> {
            // Enable input only after chatId and crypto setup are complete.
            boolean r = Boolean.TRUE.equals(ready);
            send.setEnabled(r);
            edit.setEnabled(r);
        });
    }

    private void update(List<ChatMessage> msgs) {
        // Update the adapter with decrypted messages and scroll to the bottom.
        adapter.setItems(msgs);
        recycler.scrollToPosition(Math.max(msgs.size() - 1, 0));
    }

    private void setupInput() {
        send.setOnClickListener(v -> {
            String t = edit.getText().toString();
            if (TextUtils.isEmpty(t.trim())) return;
            // Delegate to ViewModel, which encrypts and pushes to Firestore.
            vm.send(t.trim());
            edit.setText("");
            vm.updateTyping(false);
            isTypingLocal = false;
        });

        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // I only send typing updates when state changes to reduce network noise.
                boolean nowTyping = s != null && s.length() > 0;
                if (nowTyping != isTypingLocal) {
                    isTypingLocal = nowTyping;
                    vm.updateTyping(isTypingLocal);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_delete_chat) {
            showDelete();
            return true;
        } else if (id == R.id.action_verify_security) {
            // Navigate to security screen where users can verify keys / fingerprints.
            Intent intent = new Intent(this, ChatSecurityActivity.class);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_UID, contactUid);
            intent.putExtra(ChatActivity.EXTRA_CONTACT_EMAIL, contactEmail);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showDelete() {
        // Give the user the option to hide locally or delete the chat for both sides.
        new AlertDialog.Builder(this)
                .setTitle("Delete chat")
                .setMessage("Choose how to delete this chat.")
                .setNegativeButton("Cancel", null)
                .setNeutralButton("For me only", (d, w) -> vm.deleteForMe())
                .setPositiveButton("For both", (d, w) -> vm.deleteForEveryone())
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // When leaving the screen I clear typing state so remote UI does not show stale indicators.
        vm.updateTyping(false);
        isTypingLocal = false;
    }
}
