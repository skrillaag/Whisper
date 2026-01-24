package com.whisperwire.app.data;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.whisperwire.app.keystore.IdentityKeyStore;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for handling Firebase Authentication.
 * I centralize login, registration, and profile initialization here.
 */
public class AuthRepository {

    // Main Firebase authentication entry point.
    private final FirebaseAuth firebaseAuth;
    // Used to create the user's Firestore profile document.
    private final FirebaseFirestore firestore;

    public AuthRepository() {
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public FirebaseUser getCurrentUser() {
        // Returns cached FirebaseUser or null if not signed in.
        return firebaseAuth.getCurrentUser();
    }

    public Task<AuthResult> registerWithEmail(@NonNull String email, @NonNull String password) {
        // Firebase email/password registration handled by Auth backend.
        return firebaseAuth.createUserWithEmailAndPassword(email, password);
    }

    public Task<AuthResult> loginWithEmail(@NonNull String email, @NonNull String password) {
        // Delegates to Firebase Auth for credential verification.
        return firebaseAuth.signInWithEmailAndPassword(email, password);
    }

    public void logout() {
        // Clears Firebase authentication state locally and server session tokens.
        firebaseAuth.signOut();
    }

    public Task<Void> createUserProfileWithIdentityKey(@NonNull FirebaseUser firebaseUser) {
        // Create the Firestore user document with the identity public key.
        String uid = firebaseUser.getUid();
        String email = firebaseUser.getEmail();

        try {
            // I load or generate the device's identity keypair from Android Keystore.
            // Only the public key is exported and uploaded; private key stays hardware-backed.
            IdentityKeyStore identityKeyStore = new IdentityKeyStore();
            String identityPublicKeyBase64 = identityKeyStore.getIdentityPublicKeyBase64();

            // Basic user profile stored under users/{uid}.
            // identityPublicKey is essential for E2EE contact discovery.
            Map<String, Object> userData = new HashMap<>();
            userData.put("uid", uid);
            userData.put("email", email);
            userData.put("displayName", email); // Placeholder until user edits profile.
            userData.put("identityPublicKey", identityPublicKeyBase64);
            userData.put("createdAt", System.currentTimeMillis());

            // Write user profile to Firestore.
            // Security rules must restrict write access to authenticated user == uid.
            return firestore.collection("users")
                    .document(uid)
                    .set(userData);

        } catch (GeneralSecurityException | IOException e) {
            // Propagate initialization failure to caller as a Task exception.
            return Tasks.forException(e);
        }
    }
}
