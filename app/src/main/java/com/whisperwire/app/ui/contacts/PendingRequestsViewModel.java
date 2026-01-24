package com.whisperwire.app.ui.contacts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * ViewModel that maintains a real-time boolean flag indicating whether the
 * current user has any pending incoming contact requests.
 *
 * UI usage:
 * - MainActivity shows a red dot on the Contacts button.
 * - ContactsListActivity shows "●" next to the Requests button.
 *
 * Firestore data source:
 * /contact_requests where toUid = currentUser AND status = "pending".
 */
public class PendingRequestsViewModel extends ViewModel {

    // Firebase instances used for account context and snapshot listeners.
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    // LiveData exposed to UI: true if at least one pending request exists.
    private final MutableLiveData<Boolean> hasPending = new MutableLiveData<>(false);

    // Active Firestore listener (cleaned up on ViewModel clear)
    private ListenerRegistration registration;

    public PendingRequestsViewModel() {
        // Begin monitoring Firestore for pending requests immediately.
        startListening();
    }

    public LiveData<Boolean> getHasPending() {
        return hasPending;
    }

    /**
     * Subscribes to Firestore for real-time updates to pending requests.
     * If no user is logged in, sets hasPending=false.
     */
    private void startListening() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            hasPending.setValue(false);
            return;
        }

        String myUid = current.getUid();

        // Snapshot listener: real-time tracking of /contact_requests where toUid=myUid and status=pending
        registration = firestore.collection("contact_requests")
                .whereEqualTo("toUid", myUid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snap, error) -> {
                    // On error or null snapshot, UI should show "no pending"
                    if (error != null || snap == null) {
                        hasPending.postValue(false);
                        return;
                    }
                    hasPending.postValue(!snap.isEmpty());
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Remove listener to avoid leaking activity context
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }
}
