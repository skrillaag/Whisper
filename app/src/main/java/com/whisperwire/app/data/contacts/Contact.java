package com.whisperwire.app.data.contacts;

public class Contact {

    // UID of the contact user; links to users/{uid} in Firestore.
    private String contactUid;
    // Email of the contact, stored for quick display and lookups.
    private String contactEmail;
    // Timestamp (millis) when this contact entry was created.
    private long createdAt;

    public Contact() {
        // Required for Firestore document-to-object deserialization.
    }

    public Contact(String contactUid, String contactEmail, long createdAt) {
        // Represents a single entry under users/{uid}/contacts.
        this.contactUid = contactUid;
        this.contactEmail = contactEmail;
        this.createdAt = createdAt;
    }

    public String getContactUid() {
        return contactUid;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
