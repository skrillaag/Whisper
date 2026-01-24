package com.whisperwire.app.data.chat;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.whisperwire.app.crypto.MessageCryptoManager;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central repository for chat operations.
 * I handle chat creation, encrypted message send/receive, and recent chat summaries.
 */
public class ChatRepository {

    // Root collection for chat threads: chats/{chatId}.
    private static final String COLLECTION_CHATS = "chats";
    // Subcollection for messages inside a chat: chats/{chatId}/messages/{messageId}.
    private static final String SUBCOLLECTION_MESSAGES = "messages";

    // Used to resolve current user identity for all chat operations.
    private final FirebaseAuth firebaseAuth;
    // Firestore backend where chats and messages are stored (ciphertext only).
    private final FirebaseFirestore firestore;
    // High-level E2EE pipeline for encrypting/decrypting message payloads.
    private final MessageCryptoManager crypto;

    public ChatRepository() throws GeneralSecurityException, IOException {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.crypto = new MessageCryptoManager();
    }

    private String buildParticipantKey(String a, String b) {
        // Deterministic key so the same pair of users always map to the same chat.
        String[] arr = new String[]{a, b};
        Arrays.sort(arr);
        return arr[0] + "_" + arr[1];
    }

    // ---------------------------------------------------------------------
    // CREATE / GET CHAT
    // ---------------------------------------------------------------------

    public Task<String> getOrCreateChatId(@NonNull String contactUid) {
        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            return Tasks.forException(new IllegalStateException("Not logged in"));
        }

        String myUid = me.getUid();
        // participantKey ensures uniqueness for the unordered pair (me, contact).
        String key = buildParticipantKey(myUid, contactUid);

        return firestore.collection(COLLECTION_CHATS)
                .whereEqualTo("participantKey", key)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    QuerySnapshot snap = null;

                    // If query succeeded, try to reuse existing chat
                    if (task.isSuccessful()) {
                        snap = task.getResult();
                    }

                    if (snap != null && !snap.isEmpty()) {
                        // Existing chat for this pair; reuse chatId.
                        return Tasks.forResult(snap.getDocuments().get(0).getId());
                    }

                    // If query failed OR no chat exists, create a new chat
                    long now = System.currentTimeMillis();
                    Map<String, Object> data = new HashMap<>();
                    data.put("participants", Arrays.asList(myUid, contactUid));
                    data.put("participantKey", key);
                    data.put("createdAt", now);
                    data.put("lastTimestamp", now);

                    DocumentReference ref = firestore.collection(COLLECTION_CHATS).document();
                    return ref.set(data).continueWith(t -> {
                        if (!t.isSuccessful()) {
                            throw t.getException() != null ? t.getException()
                                    : new RuntimeException("Failed to create chat");
                        }
                        return ref.getId();
                    });
                });
    }


    // ---------------------------------------------------------------------
    // SEND ENCRYPTED MESSAGE
    // ---------------------------------------------------------------------

    public Task<Void> sendEncryptedMessage(@NonNull String chatId,
                                           @NonNull String contactUid,
                                           @NonNull String plaintext) {

        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            return Tasks.forException(new IllegalStateException("Not logged in"));
        }
        String myUid = me.getUid();

        // Fetch contact user profile to obtain their identity public key for ECDH.
        return firestore.collection("users")
                .document(contactUid)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return Tasks.forException(task.getException());
                    }
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        return Tasks.forException(new IllegalStateException("Contact missing"));
                    }

                    String remoteKey = doc.getString("identityPublicKey");
                    if (remoteKey == null) {
                        return Tasks.forException(new IllegalStateException("Contact missing key"));
                    }

                    long ts = System.currentTimeMillis();

                    // Encrypt plaintext with ECDH + HKDF + AES-GCM; chatId + timestamp bind the key.
                    MessageCryptoManager.EncryptedMessage enc =
                            crypto.encryptMessage(remoteKey, plaintext, chatId, ts);
                    MessageCryptoManager.EncryptedMessageEncoded encStore =
                            crypto.encodeForStorage(enc);

                    // Only ciphertext and crypto parameters are written to Firestore.
                    Map<String, Object> data = new HashMap<>();
                    data.put("fromUid", myUid);
                    data.put("iv", encStore.ivBase64);
                    data.put("ciphertext", encStore.ciphertextBase64);
                    data.put("salt", encStore.saltBase64);
                    data.put("timestamp", ts);

                    DocumentReference msgRef = firestore.collection(COLLECTION_CHATS)
                            .document(chatId)
                            .collection(SUBCOLLECTION_MESSAGES)
                            .document();

                    // Write message then update chat's last activity timestamp for recent list.
                    return msgRef.set(data)
                            .continueWithTask(t -> {
                                if (!t.isSuccessful()) {
                                    return Tasks.forException(t.getException());
                                }
                                return firestore.collection(COLLECTION_CHATS)
                                        .document(chatId)
                                        .update("lastTimestamp", ts);
                            });
                });
    }

    // ---------------------------------------------------------------------
    // LISTEN TO MESSAGES
    // ---------------------------------------------------------------------

    public ListenerRegistration listenToMessages(@NonNull String chatId,
                                                 @NonNull String remoteKeyBase64,
                                                 @NonNull MessagesListener listener) {

        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            throw new IllegalStateException("Not logged in");
        }
        String myUid = me.getUid();

        // Live listener over chats/{chatId}/messages ordered by timestamp.
        return firestore.collection(COLLECTION_CHATS)
                .document(chatId)
                .collection(SUBCOLLECTION_MESSAGES)
                .orderBy("timestamp")
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onError(error);
                        return;
                    }

                    List<ChatMessage> out = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String fromUid = doc.getString("fromUid");
                        String iv = doc.getString("iv");
                        String ct = doc.getString("ciphertext");
                        String salt = doc.getString("salt");
                        Long ts = doc.getLong("timestamp");

                        // Skip malformed or incomplete records.
                        if (fromUid == null || iv == null || ct == null || salt == null || ts == null) {
                            continue;
                        }

                        try {
                            // Decode Base64 fields back to raw crypto material.
                            MessageCryptoManager.EncryptedMessage encDecoded =
                                    crypto.decodeFromStorage(iv, ct, salt);

                            // Decrypt using the remote identity key and chatId/timestamp context.
                            String plaintext = crypto.decryptMessage(
                                    remoteKeyBase64,
                                    encDecoded.getIv(),
                                    encDecoded.getCiphertext(),
                                    encDecoded.getSalt(),
                                    chatId,
                                    ts
                            );

                            boolean mine = fromUid.equals(myUid);
                            out.add(new ChatMessage(
                                    doc.getId(),
                                    fromUid,
                                    plaintext,
                                    ts,
                                    mine
                            ));
                        } catch (Exception cryptoError) {
                            // Decryption failed (e.g. old format, wrong key, AEAD tag)
                            // Skip this message but keep processing the rest.
                        }
                    }

                    // I deliver only successfully decrypted messages to the UI.
                    listener.onMessages(out);
                });
    }


    // ---------------------------------------------------------------------
    // MARK CHAT READ (UNREAD DOT SUPPORT)
    // ---------------------------------------------------------------------

    public Task<Void> markChatRead(@NonNull String chatId, long lastReadTimestamp) {
        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            return Tasks.forException(new IllegalStateException("Not logged in"));
        }

        String myUid = me.getUid();

        // Per-user chat metadata under users/{uid}/chats/{chatId}.
        DocumentReference userChatRef = firestore.collection("users")
                .document(myUid)
                .collection("chats")
                .document(chatId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("lastReadTimestamp", lastReadTimestamp);

        // Aggregate field on chats/{chatId} to track read status per user.
        String fieldName = "lastRead_" + myUid;
        DocumentReference chatRef = firestore.collection(COLLECTION_CHATS)
                .document(chatId);

        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put(fieldName, lastReadTimestamp);

        // Update user-specific metadata then shared chat-level read marker.
        return userChatRef.set(userData)
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) {
                        return Tasks.forException(t.getException());
                    }
                    return chatRef.update(chatUpdate);
                });
    }

    // ---------------------------------------------------------------------
    // DELETE CHAT (FOR ME ONLY)
    // ---------------------------------------------------------------------

    public Task<Void> deleteChatForMe(@NonNull String chatId) {
        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            return Tasks.forException(new IllegalStateException("Not logged in"));
        }

        String myUid = me.getUid();

        DocumentReference chatRef = firestore.collection(COLLECTION_CHATS).document(chatId);
        DocumentReference metaRef = firestore.collection("users")
                .document(myUid)
                .collection("chats")
                .document(chatId);

        // Mark the chat as hidden for this user while preserving messages for others.
        Map<String, Object> update = new HashMap<>();
        update.put("hidden_" + myUid, true);

        Task<Void> t1 = chatRef.update(update);
        Task<Void> t2 = metaRef.delete();

        return Tasks.whenAll(t1, t2);
    }

    // ---------------------------------------------------------------------
    // DELETE CHAT (FOR EVERYONE)
    // ---------------------------------------------------------------------

    public Task<Void> deleteChatForEveryone(@NonNull String chatId) {
        DocumentReference chatRef = firestore.collection(COLLECTION_CHATS).document(chatId);

        // Fully remove chat for all participants: meta + messages + chat doc.
        return chatRef.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                return Tasks.forException(task.getException());
            }
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) {
                return Tasks.forResult(null);
            }

            List<String> participants = (List<String>) doc.get("participants");
            if (participants == null) {
                participants = new ArrayList<>();
            }

            List<Task<Void>> all = new ArrayList<>();

            // Delete per-user chat metadata under users/{uid}/chats/{chatId}.
            for (String uid : participants) {
                DocumentReference meta = firestore.collection("users")
                        .document(uid)
                        .collection("chats")
                        .document(chatId);
                all.add(meta.delete());
            }

            // Delete all messages then delete the chat document itself.
            Task<Void> deleteMessages = deleteSubcollection(
                    chatRef.collection(SUBCOLLECTION_MESSAGES)
            ).continueWithTask(t -> chatRef.delete());

            all.add(deleteMessages);

            return Tasks.whenAll(all);
        });
    }

    private Task<Void> deleteSubcollection(CollectionReference ref) {
        // Helper to delete a single-page subcollection by deleting each document.
        return ref.get().continueWithTask(task -> {
            if (!task.isSuccessful()) {
                return Tasks.forException(task.getException());
            }
            QuerySnapshot snap = task.getResult();
            if (snap == null || snap.isEmpty()) {
                return Tasks.forResult(null);
            }

            List<Task<Void>> deletes = new ArrayList<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                deletes.add(doc.getReference().delete());
            }
            return Tasks.whenAll(deletes);
        });
    }

    // ---------------------------------------------------------------------
    // RECENT CHATS (WITH PREVIEW)
    // ---------------------------------------------------------------------

    public ListenerRegistration listenToRecentChatsForCurrentUser(
            @NonNull RecentChatsListener listener) {

        FirebaseUser me = firebaseAuth.getCurrentUser();
        if (me == null) {
            throw new IllegalStateException("Not logged in");
        }
        String myUid = me.getUid();

        // Listen to chats where the current user is a participant, ordered by last activity.
        return firestore.collection(COLLECTION_CHATS)
                .whereArrayContains("participants", myUid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null || snap == null) {
                        listener.onError(error);
                        return;
                    }

                    List<Task<ChatSummary>> tasks = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String chatId = doc.getId();
                        List<String> participants = (List<String>) doc.get("participants");
                        Long lastTs = doc.getLong("lastTimestamp");
                        if (participants == null || participants.size() != 2 || lastTs == null) {
                            continue;
                        }

                        // Per-user hidden flag to support "delete for me" behavior.
                        Boolean hidden = doc.getBoolean("hidden_" + myUid);
                        if (Boolean.TRUE.equals(hidden)) {
                            continue;
                        }

                        // Resolve the other participant as the contactUid.
                        String contactUid = participants.get(0).equals(myUid)
                                ? participants.get(1)
                                : participants.get(0);

                        String lastReadField = "lastRead_" + myUid;
                        Long lastReadVal = doc.getLong(lastReadField);
                        long lastRead = lastReadVal != null ? lastReadVal : 0L;
                        boolean hasUnread = lastTs > lastRead;

                        DocumentReference userRef = firestore.collection("users")
                                .document(contactUid);

                        CollectionReference messagesRef = firestore.collection(COLLECTION_CHATS)
                                .document(chatId)
                                .collection(SUBCOLLECTION_MESSAGES);

                        // Fetch contact profile (email + identity key) and last message for preview.
                        Task<DocumentSnapshot> userTask = userRef.get();
                        Task<QuerySnapshot> lastMsgTask = messagesRef
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(1)
                                .get();

                        Task<ChatSummary> chatTask = Tasks
                                .whenAllSuccess(userTask, lastMsgTask)
                                .continueWith(t -> {
                                    DocumentSnapshot userDoc = userTask.getResult();
                                    QuerySnapshot lastMsgSnap = lastMsgTask.getResult();

                                    String email = null;
                                    if (userDoc != null && userDoc.exists()) {
                                        email = userDoc.getString("email");
                                    }
                                    if (email == null) {
                                        email = "(unknown)";
                                    }

                                    String remoteKey = null;
                                    if (userDoc != null && userDoc.exists()) {
                                        remoteKey = userDoc.getString("identityPublicKey");
                                    }

                                    String preview = null;
                                    try {
                                        // Decrypt the most recent message to show a local plaintext preview.
                                        if (lastMsgSnap != null && !lastMsgSnap.isEmpty()
                                                && remoteKey != null) {
                                            DocumentSnapshot m = lastMsgSnap.getDocuments().get(0);
                                            String iv = m.getString("iv");
                                            String ct = m.getString("ciphertext");
                                            String salt = m.getString("salt");
                                            Long tsMsg = m.getLong("timestamp");

                                            if (iv != null && ct != null && salt != null && tsMsg != null) {
                                                MessageCryptoManager.EncryptedMessage enc =
                                                        crypto.decodeFromStorage(iv, ct, salt);

                                                String plaintext = crypto.decryptMessage(
                                                        remoteKey,
                                                        enc.getIv(),
                                                        enc.getCiphertext(),
                                                        enc.getSalt(),
                                                        chatId,
                                                        tsMsg
                                                );

                                                preview = shortenPreview(plaintext);
                                            }
                                        } else if (lastMsgSnap != null && lastMsgSnap.isEmpty()) {
                                            // No messages yet in this chat.
                                        }
                                    } catch (Exception e) {
                                        // If preview decryption fails, I indicate this to the user.
                                        preview = "(unable to decrypt)";
                                    }

                                    if (preview == null) {
                                        preview = "(no messages yet)";
                                    }

                                    return new ChatSummary(
                                            chatId,
                                            contactUid,
                                            email,
                                            lastTs,
                                            hasUnread,
                                            preview
                                    );
                                });

                        tasks.add(chatTask);
                    }

                    if (tasks.isEmpty()) {
                        listener.onChats(new ArrayList<>());
                        return;
                    }

                    // Aggregate all ChatSummary tasks and push result list to the UI.
                    Tasks.whenAllSuccess(tasks)
                            .addOnSuccessListener(results -> {
                                List<ChatSummary> list = new ArrayList<>();
                                for (Object o : results) {
                                    if (o instanceof ChatSummary) {
                                        list.add((ChatSummary) o);
                                    }
                                }
                                listener.onChats(list);
                            })
                            .addOnFailureListener(listener::onError);
                });
    }

    private String shortenPreview(String text) {
        if (text == null) return null;
        String trimmed = text.replace("\n", " ").trim();
        if (trimmed.length() <= 40) return trimmed;
        return trimmed.substring(0, 40) + "…";
    }

    // ---------------------------------------------------------------------
    // INTERFACES
    // ---------------------------------------------------------------------

    public interface MessagesListener {
        // Called whenever the message snapshot for a chat changes.
        void onMessages(List<ChatMessage> messages);
        void onError(Exception e);
    }

    public interface RecentChatsListener {
        // Called with decrypted recent chat summaries for the current user.
        void onChats(List<ChatSummary> list);
        void onError(Exception e);
    }
}
