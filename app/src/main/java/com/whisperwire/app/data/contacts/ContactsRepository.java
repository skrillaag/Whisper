package com.whisperwire.app.data.contacts;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Repository for managing contact requests and contact lists in Firestore.
public class ContactsRepository {

    // Used to identify the current user for contact operations.
    private final FirebaseAuth firebaseAuth;
    // Firestore instance backing contact_requests and users/{uid}/contacts.
    private final FirebaseFirestore firestore;

    public ContactsRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public Task<Void> sendContactRequestByEmail(@NonNull String targetEmail) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            return Tasks.forException(new IllegalStateException("User not logged in"));
        }

        String fromUid = current.getUid();
        String fromEmail = current.getEmail();

        // Lookup target user by email in users collection, then create contact_requests document.
        return firestore.collection("users")
                .whereEqualTo("email", targetEmail)
                .limit(1)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        return Tasks.forException(task.getException());
                    }

                    if (task.getResult().isEmpty()) {
                        return Tasks.forException(new IllegalArgumentException("User not found"));
                    }

                    DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                    String toUid = doc.getString("uid");
                    if (toUid == null) {
                        return Tasks.forException(new IllegalStateException("Invalid user record"));
                    }

                    // contact_requests/{autoId} holds pending invitations between users.
                    Map<String, Object> requestData = new HashMap<>();
                    requestData.put("fromUid", fromUid);
                    requestData.put("toUid", toUid);
                    requestData.put("fromEmail", fromEmail);
                    requestData.put("status", "pending");
                    requestData.put("createdAt", System.currentTimeMillis());

                    return firestore.collection("contact_requests")
                            .add(requestData)
                            .continueWithTask(addTask -> {
                                if (!addTask.isSuccessful()) {
                                    return Tasks.forException(addTask.getException());
                                }
                                // Caller only needs success/failure, not the id here.
                                return Tasks.forResult(null);
                            });
                });
    }

    public Task<List<ContactRequest>> getIncomingRequests() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            return Tasks.forException(new IllegalStateException("User not logged in"));
        }

        String currentUid = current.getUid();

        // Query pending requests where the current user is the recipient.
        return firestore.collection("contact_requests")
                .whereEqualTo("toUid", currentUid)
                .whereEqualTo("status", "pending")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() : new RuntimeException("Query failed");
                    }

                    List<ContactRequest> result = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        String id = doc.getId();
                        String fromUid = doc.getString("fromUid");
                        String toUid = doc.getString("toUid");
                        String fromEmail = doc.getString("fromEmail");
                        String status = doc.getString("status");
                        Long createdAt = doc.getLong("createdAt");

                        // Skip malformed records to avoid crashing the UI layer.
                        if (fromUid == null || toUid == null || status == null || createdAt == null) {
                            continue;
                        }

                        result.add(new ContactRequest(
                                id,
                                fromUid,
                                toUid,
                                fromEmail,
                                status,
                                createdAt
                        ));
                    }
                    return result;
                });
    }

    public Task<Void> acceptContactRequest(@NonNull ContactRequest request) {
        if (request.getId() == null) {
            return Tasks.forException(new IllegalArgumentException("Request id is null"));
        }

        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            return Tasks.forException(new IllegalStateException("User not logged in"));
        }

        String currentUid = current.getUid();
        String currentEmail = current.getEmail();

        String fromUid = request.getFromUid();
        String toUid = request.getToUid();

        // contact_requests/{id} document to update status.
        DocumentReference requestRef = firestore.collection("contact_requests")
                .document(request.getId());

        // users/{fromUid}/contacts/{toUid}
        DocumentReference fromContactRef = firestore.collection("users")
                .document(fromUid)
                .collection("contacts")
                .document(toUid);

        // users/{toUid}/contacts/{fromUid}
        DocumentReference toContactRef = firestore.collection("users")
                .document(toUid)
                .collection("contacts")
                .document(fromUid);

        // Transaction ensures both contact entries and request status are updated atomically.
        return firestore.runTransaction(transaction -> {
            long now = System.currentTimeMillis();

            // From user's contact (they see current user)
            Map<String, Object> fromContact = new HashMap<>();
            fromContact.put("contactUid", toUid);
            fromContact.put("contactEmail", currentEmail);
            fromContact.put("createdAt", now);

            // Current user's contact (they see from user)
            Map<String, Object> toContact = new HashMap<>();
            toContact.put("contactUid", fromUid);
            toContact.put("contactEmail", request.getFromEmail());
            toContact.put("createdAt", now);

            transaction.update(requestRef, "status", "accepted");
            transaction.set(fromContactRef, fromContact);
            transaction.set(toContactRef, toContact);

            return null;
        });
    }

    public Task<Void> rejectContactRequest(@NonNull ContactRequest request) {
        if (request.getId() == null) {
            return Tasks.forException(new IllegalArgumentException("Request id is null"));
        }

        // Mark the contact_requests/{id} document as rejected.
        DocumentReference requestRef = firestore.collection("contact_requests")
                .document(request.getId());

        return requestRef.update("status", "rejected");
    }

    public Task<List<Contact>> getContactsForCurrentUser() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            return Tasks.forException(new IllegalStateException("User not logged in"));
        }

        String uid = current.getUid();

        // Read users/{uid}/contacts ordered by createdAt for stable UI display.
        return firestore.collection("users")
                .document(uid)
                .collection("contacts")
                .orderBy("createdAt")
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null ? task.getException() : new RuntimeException("Query failed");
                    }

                    List<Contact> result = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        String contactUid = doc.getString("contactUid");
                        String contactEmail = doc.getString("contactEmail");
                        Long createdAt = doc.getLong("createdAt");

                        // Ignore partially populated contact docs for robustness.
                        if (contactUid == null || contactEmail == null || createdAt == null) {
                            continue;
                        }

                        result.add(new Contact(contactUid, contactEmail, createdAt));
                    }
                    return result;
                });
    }
}
