package com.whisperwire.app.data.chat;

public class ChatMessage {

    // Firestore document ID for this message under chats/{chatId}/messages.
    private String id;
    // UID of the sender (used to determine message direction).
    private String fromUid;
    // Decrypted plaintext content shown in the UI; ciphertext is stored separately.
    private String text;
    // Unix timestamp (millis) when the message was created/sent.
    private long timestamp;
    // True if this message was sent by the current user; used for UI alignment.
    private boolean mine;

    public ChatMessage() {
        // Required for Firestore deserialization.
    }

    public ChatMessage(String id, String fromUid, String text, long timestamp, boolean mine) {
        // Represents a single decrypted message in the chat stream.
        this.id = id;
        this.fromUid = fromUid;
        this.text = text;
        this.timestamp = timestamp;
        this.mine = mine;
    }

    public String getId() {
        return id;
    }

    public String getFromUid() {
        return fromUid;
    }

    public String getText() {
        // Plaintext after AES-GCM decryption performed in ChatRepository.
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isMine() {
        return mine;
    }
}
