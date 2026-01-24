package com.whisperwire.app.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes a user's account and associated Firestore data.
 *
 * Performs client-side, multi-step cleanup of:
 * - contact requests involving the user
 * - user subcollections (contacts, chats metadata)
 * - chats and their messages where the user is a participant
 * - the user document in Firestore
 * - the FirebaseAuth user account
 */
public class AccountDeletionRepository {

    // I use FirebaseAuth to locate and delete the authenticated user.
    private final FirebaseAuth firebaseAuth;
    // Firestore instance used to traverse and delete user-owned documents.
    private final FirebaseFirestore firestore;

    public AccountDeletionRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public Task<Void> deleteCurrentUserAndData() {
        // I require an authenticated user before starting deletion.
        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) {
            return Tasks.forException(new IllegalStateException("No user logged in"));
        }
        String uid = user.getUid();

        // Chain: contact_requests -> user subcollections -> chats -> user doc -> auth user
        // I enforce ordering so references to the user are removed before deleting the auth account.
        return deleteContactRequests(uid)
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    return deleteUserSubcollections(uid);
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    return deleteChatsForUser(uid);
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    return deleteUserDocument(uid);
                })
                .continueWithTask(t -> {
                    if (!t.isSuccessful()) return Tasks.forException(t.getException());
                    return deleteAuthUser(user);
                });
    }

    private Task<Void> deleteContactRequests(@NonNull String uid) {
        // I delete all contact_requests where the user is either sender or recipient.
        CollectionReference ref = firestore.collection("contact_requests");

        Task<QuerySnapshot> fromTask = ref.whereEqualTo("fromUid", uid).get();
        Task<QuerySnapshot> toTask = ref.whereEqualTo("toUid", uid).get();

        return Tasks.whenAllSuccess(fromTask, toTask)
                .continueWithTask(task -> {
                    List<Task<Void>> deletes = new ArrayList<>();

                    QuerySnapshot fromSnap = fromTask.getResult();
                    if (fromSnap != null) {
                        for (DocumentSnapshot doc : fromSnap.getDocuments()) {
                            deletes.add(doc.getReference().delete());
                        }
                    }

                    QuerySnapshot toSnap = toTask.getResult();
                    if (toSnap != null) {
                        for (DocumentSnapshot doc : toSnap.getDocuments()) {
                            deletes.add(doc.getReference().delete());
                        }
                    }

                    if (deletes.isEmpty()) {
                        // Nothing to delete; I return a completed Task.
                        return Tasks.forResult(null);
                    }
                    // Aggregate all deletions so the caller can await completion.
                    return Tasks.whenAll(deletes);
                });
    }

    private Task<Void> deleteUserSubcollections(@NonNull String uid) {
        // I remove user-owned subcollections under users/{uid}.
        DocumentReference userRef = firestore.collection("users").document(uid);

        Task<Void> deleteContacts = deleteSubcollection(userRef.collection("contacts"));
        Task<Void> deleteChatsMeta = deleteSubcollection(userRef.collection("chats"));

        return Tasks.whenAll(deleteContacts, deleteChatsMeta);
    }

    private Task<Void> deleteSubcollection(CollectionReference subRef) {
        // Generic helper to delete all documents in a subcollection (single page).
        return subRef.get().continueWithTask(task -> {
            if (!task.isSuccessful()) return Tasks.forException(task.getException());

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

    private Task<Void> deleteChatsForUser(@NonNull String uid) {
        // I find all chats where the user participates via participants array.
        CollectionReference chatsRef = firestore.collection("chats");

        return chatsRef.whereArrayContains("participants", uid)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) return Tasks.forException(task.getException());

                    QuerySnapshot snap = task.getResult();
                    if (snap == null || snap.isEmpty()) {
                        return Tasks.forResult(null);
                    }

                    List<Task<Void>> chatDeletes = new ArrayList<>();

                    for (DocumentSnapshot chatDoc : snap.getDocuments()) {
                        DocumentReference chatRef = chatDoc.getReference();
                        // Delete messages subcollection then chat doc
                        // I ensure message history is wiped before removing the chat metadata.
                        Task<Void> deleteMessagesTask = deleteSubcollection(chatRef.collection("messages"))
                                .continueWithTask(t2 -> {
                                    if (!t2.isSuccessful()) {
                                        return Tasks.forException(t2.getException());
                                    }
                                    return chatRef.delete();
                                });
                        chatDeletes.add(deleteMessagesTask);
                    }

                    return Tasks.whenAll(chatDeletes);
                });
    }

    private Task<Void> deleteUserDocument(@NonNull String uid) {
        // Final Firestore cleanup: remove the users/{uid} profile document.
        DocumentReference userRef = firestore.collection("users").document(uid);
        return userRef.delete();
    }

    private Task<Void> deleteAuthUser(FirebaseUser user) {
        // FirebaseAuth requires recent login for delete();
        // for this project we assume user is recently authenticated.
        // This removes the account from Firebase Auth after Firestore cleanup.
        return user.delete();
    }
}
