package com.whisperwire.app.data.user;

public class User {

    // Firebase Auth UID identifying the user across Firestore collections.
    private String uid;
    // Email associated with the Firebase account; stored as metadata only.
    private String email;
    // User-facing name shown in chats and contact lists.
    private String displayName;
    // Base64-encoded X.509 EC public key used for E2EE (shared via Firestore).
    private String identityPublicKey;
    // Firestore timestamp (millis) marking account creation.
    private long createdAt;

    public User() {
        // Firestore deserialization requires a public no-arg constructor.
    }

    public User(String uid,
                String email,
                String displayName,
                String identityPublicKey,
                long createdAt) {
        // Immutable user profile snapshot stored under users/{uid}.
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.identityPublicKey = identityPublicKey;
        this.createdAt = createdAt;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIdentityPublicKey() {
        // Public identity key used for ECDH; never stores the private component.
        return identityPublicKey;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
