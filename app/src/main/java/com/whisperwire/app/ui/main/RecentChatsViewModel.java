package com.whisperwire.app.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.whisperwire.app.data.chat.ChatRepository;
import com.whisperwire.app.data.chat.ChatSummary;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * ViewModel for the MainActivity recent chats screen.
 *
 * Responsibilities:
 *   • Creates and owns a ChatRepository instance (crypto-capable).
 *   • Subscribes to Firestore "recent chats" live updates.
 *   • Exposes LiveData for UI: list of ChatSummary and error messages.
 *   • Manages lifecycle of Firestore listeners.
 *
 * This ViewModel holds no Android context, only data state.
 */
public class RecentChatsViewModel extends ViewModel {

    // Repository performing Firestore queries & message decryption
    private final ChatRepository chatRepository;

    // Firestore listener for real-time chat updates
    private ListenerRegistration registration;

    // LiveData exposed to MainActivity
    private final MutableLiveData<List<ChatSummary>> chats = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public RecentChatsViewModel() {
        try {
            // Initialize repository (includes crypto manager and Firestore)
            this.chatRepository = new ChatRepository();
        } catch (GeneralSecurityException | IOException e) {
            // Crypto layer initialization failure → fatal for app startup
            throw new RuntimeException(e);
        }
    }

    public LiveData<List<ChatSummary>> getChats() {
        return chats;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * Starts the real-time subscription to recent chats.
     * Called once from MainActivity.onCreate().
     *
     * ChatRepository handles:
     *   - Fetching friend identity keys
     *   - Decrypting last message previews
     *   - Tracking unread state per chat
     */
    public void start() {
        registration = chatRepository.listenToRecentChatsForCurrentUser(
                new ChatRepository.RecentChatsListener() {

                    @Override
                    public void onChats(List<ChatSummary> list) {
                        // Post to LiveData on background thread
                        chats.postValue(list);
                    }

                    @Override
                    public void onError(Exception e) {
                        if (e != null) {
                            error.postValue(e.getMessage());
                        }
                    }
                }
        );
    }

    /**
     * ViewModel lifecycle cleanup.
     * Removes Firestore listener to avoid memory leaks.
     */
    @Override
    protected void onCleared() {
        if (registration != null) {
            registration.remove();
        }
    }
}
