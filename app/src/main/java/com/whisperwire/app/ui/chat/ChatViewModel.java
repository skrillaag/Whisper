package com.whisperwire.app.ui.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.whisperwire.app.data.chat.ChatMessage;
import com.whisperwire.app.data.chat.ChatRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatViewModel extends ViewModel {

    // Direct Firestore access for typing indicators and lightweight chat metadata.
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    // Used to resolve current user's UID for typing and send operations.
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    // Central repository handling encrypted chat operations and Firestore I/O.
    private final ChatRepository repo;

    // chats/{chatId} identifier for this conversation.
    private String chatId;
    // UID of the remote contact.
    private String contactUid;
    // Base64-encoded identity public key of the remote user (used for E2EE).
    private String remoteKey;

    // Listener for message updates.
    private ListenerRegistration reg;
    // Listener for remote typing status under chats/{chatId}/typing/{contactUid}.
    private ListenerRegistration typingReg;

    // LiveData for decrypted messages and UI state.
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleteSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> deleteError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> remoteTyping = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> chatReady = new MutableLiveData<>(false);

    // Tracks last typing value I sent to avoid spamming Firestore.
    private boolean lastTypingSent = false;

    public ChatViewModel() {
        try {
            // Initialize the repository; this wires up the crypto pipeline for this ViewModel.
            repo = new ChatRepository();
        } catch (GeneralSecurityException | IOException e) {
            // Crypto initialization is critical; fail fast if it cannot be set up.
            throw new RuntimeException(e);
        }
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Boolean> getDeleteSuccess() {
        return deleteSuccess;
    }

    public LiveData<String> getDeleteError() {
        return deleteError;
    }

    public LiveData<Boolean> getRemoteTyping() {
        return remoteTyping;
    }

    public LiveData<Boolean> getChatReady() {
        return chatReady;
    }

    // Initializes the chat by loading the contact's identity key and resolving chatId.
    public void init(String contactUid) {
        this.contactUid = contactUid;

        firestore.collection("users")
                .document(contactUid)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        error.setValue("Failed to load contact");
                        return;
                    }
                    DocumentSnapshot d = task.getResult();
                    if (d == null || !d.exists()) {
                        error.setValue("Contact missing");
                        return;
                    }
                    // Remote identity public key is required for ECDH/HKDF/AES-GCM.
                    remoteKey = d.getString("identityPublicKey");
                    if (remoteKey == null) {
                        error.setValue("Contact missing key");
                        return;
                    }

                    // Ensure there is a unique chats/{chatId} document for this pair of users.
                    repo.getOrCreateChatId(contactUid)
                            .addOnCompleteListener(t -> {
                                if (!t.isSuccessful()) {
                                    error.setValue("Failed to open chat");
                                    return;
                                }
                                chatId = t.getResult();
                                listenMessages();
                                listenTyping();
                                // At this point encryption and listeners are ready for use.
                                chatReady.setValue(true);
                            });
                });
    }


    private void listenMessages() {
        if (chatId == null || remoteKey == null) return;

        if (reg != null) reg.remove();

        // Subscribe to chats/{chatId}/messages and decrypt via ChatRepository.
        reg = repo.listenToMessages(chatId, remoteKey, new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(List<ChatMessage> msgs) {
                messages.setValue(msgs);
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e != null ? e.getMessage() : "Message error");
            }
        });
    }

    private void listenTyping() {
        if (chatId == null || contactUid == null) return;

        if (typingReg != null) typingReg.remove();

        // Listen for remote typing state under chats/{chatId}/typing/{contactUid}.
        typingReg = firestore.collection("chats")
                .document(chatId)
                .collection("typing")
                .document(contactUid)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null || !snap.exists()) {
                        remoteTyping.setValue(false);
                        return;
                    }
                    Boolean isTyping = snap.getBoolean("isTyping");
                    Long updatedAt = snap.getLong("updatedAt");
                    long now = System.currentTimeMillis();

                    // Consider typing valid only if it is recent to avoid stale indicators.
                    if (Boolean.TRUE.equals(isTyping) && updatedAt != null && now - updatedAt <= 5000) {
                        remoteTyping.setValue(true);
                    } else {
                        remoteTyping.setValue(false);
                    }
                });
    }

    public void send(String text) {
        // If not ready yet, ignore (UI should normally prevent this)
        if (chatId == null) {
            return;
        }

        // Delegates to ChatRepository which encrypts and writes ciphertext to Firestore.
        repo.sendEncryptedMessage(chatId, contactUid, text)
                .addOnSuccessListener(v -> {
                    // After sending, mark chat as read for this user
                    long now = System.currentTimeMillis();
                    repo.markChatRead(chatId, now);
                })
                .addOnFailureListener(e -> error.setValue(e.getMessage()));
    }


    public void deleteForMe() {
        if (chatId == null) return;
        // Marks chat hidden for the current user but preserves messages for the other side.
        repo.deleteChatForMe(chatId).addOnCompleteListener(t -> {
            if (t.isSuccessful()) deleteSuccess.setValue(true);
            else deleteError.setValue("Failed to delete for me");
        });
    }

    public void deleteForEveryone() {
        if (chatId == null) return;
        // Deletes chat metadata and messages for all participants.
        repo.deleteChatForEveryone(chatId).addOnCompleteListener(t -> {
            if (t.isSuccessful()) deleteSuccess.setValue(true);
            else deleteError.setValue("Failed to delete for everyone");
        });
    }

    public void updateTyping(boolean isTyping) {
        if (chatId == null) return;

        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) return;

        // Avoid writing the same typing state repeatedly.
        if (lastTypingSent == isTyping) return;
        lastTypingSent = isTyping;

        String myUid = me.getUid();
        Map<String, Object> data = new HashMap<>();
        data.put("isTyping", isTyping);
        data.put("updatedAt", System.currentTimeMillis());

        // Typing indicator document: chats/{chatId}/typing/{myUid}.
        firestore.collection("chats")
                .document(chatId)
                .collection("typing")
                .document(myUid)
                .set(data);
    }

    @Override
    protected void onCleared() {
        // Clean up Firestore listeners when the ViewModel is destroyed.
        if (reg != null) reg.remove();
        if (typingReg != null) typingReg.remove();
    }
}
