package com.whisperwire.app.data.chat;

public class ChatSummary {

    // Chat document ID under chats/{chatId}.
    private final String chatId;
    // UID of the other participant in this one-to-one chat.
    private final String contactUid;
    // Contact's email for display in the recent chats list.
    private final String contactEmail;
    // Timestamp of the most recent message (used for sorting).
    private final long lastTimestamp;
    // Indicates whether the chat contains unread messages for the current user.
    private final boolean hasUnread;
    // Local plaintext preview of the last message (decrypted by ChatRepository).
    private final String lastMessagePreview;

    public ChatSummary(String chatId,
                       String contactUid,
                       String contactEmail,
                       long lastTimestamp,
                       boolean hasUnread,
                       String lastMessagePreview) {
        // Immutable snapshot used in the UI for recent chat entries.
        this.chatId = chatId;
        this.contactUid = contactUid;
        this.contactEmail = contactEmail;
        this.lastTimestamp = lastTimestamp;
        this.hasUnread = hasUnread;
        this.lastMessagePreview = lastMessagePreview;
    }

    public String getChatId() {
        return chatId;
    }

    public String getContactUid() {
        return contactUid;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    public boolean hasUnread() {
        return hasUnread;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }
}
