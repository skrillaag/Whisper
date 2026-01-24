package com.whisperwire.app.ui.contacts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.whisperwire.app.data.contacts.Contact;
import com.whisperwire.app.data.contacts.ContactRequest;
import com.whisperwire.app.data.contacts.ContactsRepository;

import java.util.List;

/**
 * ViewModel for contact management flows.
 * Wraps ContactsRepository and exposes UI-ready LiveData for:
 * - sending requests
 * - loading incoming requests
 * - accepting/rejecting requests
 * - loading accepted contacts
 *
 * All Firebase operations occur in ContactsRepository; ViewModel only updates LiveData.
 */
public class ContactsViewModel extends ViewModel {

    // Repository handling Firestore operations (requests, contacts)
    private final ContactsRepository contactsRepository = new ContactsRepository();

    // LiveData for request result and error reporting
    private final MutableLiveData<Boolean> requestSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> requestErrorMessage = new MutableLiveData<>();

    // LiveData for incoming pending contact requests
    private final MutableLiveData<List<ContactRequest>> incomingRequests = new MutableLiveData<>();

    // Generic UI action message (e.g., accepted, rejected, sent)
    private final MutableLiveData<String> actionMessage = new MutableLiveData<>();

    // LiveData for current user's accepted contact list
    private final MutableLiveData<List<Contact>> contacts = new MutableLiveData<>();

    public LiveData<Boolean> getRequestSuccess() { return requestSuccess; }
    public LiveData<String> getRequestErrorMessage() { return requestErrorMessage; }
    public LiveData<List<ContactRequest>> getIncomingRequests() { return incomingRequests; }
    public LiveData<String> getActionMessage() { return actionMessage; }
    public LiveData<List<Contact>> getContacts() { return contacts; }

    /**
     * Sends a contact request by email.
     * Updates requestSuccess / requestErrorMessage accordingly.
     */
    public void sendRequest(String email) {
        contactsRepository.sendContactRequestByEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        requestSuccess.setValue(true);
                        actionMessage.setValue("Request sent");
                    } else {
                        requestSuccess.setValue(false);
                        if (task.getException() != null) {
                            // Surface repository-level failure reason to UI
                            requestErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            requestErrorMessage.setValue("Request failed");
                        }
                    }
                });
    }

    /**
     * Loads all pending incoming requests for the current user.
     * Firestore path: /contact_requests where toUid = currentUser.
     */
    public void loadIncomingRequests() {
        contactsRepository.getIncomingRequests()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        incomingRequests.setValue(task.getResult());
                    } else {
                        if (task.getException() != null) {
                            requestErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            requestErrorMessage.setValue("Failed to load requests");
                        }
                    }
                });
    }

    /**
     * Accepts a pending contact request.
     * Repository transaction writes contact docs for both users.
     * After success, refreshes requests + contacts list.
     */
    public void acceptRequest(ContactRequest request) {
        contactsRepository.acceptContactRequest(request)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (task.getException() != null) {
                            requestErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            requestErrorMessage.setValue("Failed to accept request");
                        }
                    } else {
                        actionMessage.setValue("Request accepted");
                        loadIncomingRequests(); // refresh pending list
                        loadContacts();          // refresh user's contact list
                    }
                });
    }

    /**
     * Rejects a pending contact request via simple status update.
     * Does not modify user contact lists.
     */
    public void rejectRequest(ContactRequest request) {
        contactsRepository.rejectContactRequest(request)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        if (task.getException() != null) {
                            requestErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            requestErrorMessage.setValue("Failed to reject request");
                        }
                    } else {
                        actionMessage.setValue("Request rejected");
                        loadIncomingRequests(); // refresh pending request list
                    }
                });
    }

    /**
     * Loads all accepted contacts under /users/{uid}/contacts.
     */
    public void loadContacts() {
        contactsRepository.getContactsForCurrentUser()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        contacts.setValue(task.getResult());
                    } else {
                        if (task.getException() != null) {
                            requestErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            requestErrorMessage.setValue("Failed to load contacts");
                        }
                    }
                });
    }
}
