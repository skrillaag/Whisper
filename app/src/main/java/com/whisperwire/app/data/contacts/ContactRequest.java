package com.whisperwire.app.data.contacts;

public class ContactRequest {

    // Firestore document ID for this request (contact_requests/{id}).
    private String id;
    // UID of the user who initiated the contact request.
    private String fromUid;
    // UID of the user receiving the request.
    private String toUid;
    // Email of the requester for quick display purposes.
    private String fromEmail;
    // Current request status (e.g. pending, accepted, rejected).
    private String status;
    // Timestamp (millis) when the request was created.
    private long createdAt;

    public ContactRequest() {
        // Required for Firestore deserialization.
    }

    public ContactRequest(String id,
                          String fromUid,
                          String toUid,
                          String fromEmail,
                          String status,
                          long createdAt) {
        // Represents a single outgoing or incoming contact request.
        this.id = id;
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.fromEmail = fromEmail;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getFromUid() {
        return fromUid;
    }

    public String getToUid() {
        return toUid;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
