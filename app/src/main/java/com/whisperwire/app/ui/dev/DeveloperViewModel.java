package com.whisperwire.app.ui.dev;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.whisperwire.app.crypto.CryptoBenchmark;

import java.security.GeneralSecurityException;

/**
 * ViewModel backing DeveloperActivity.
 * Provides:
 * - identityPublicKey: fetched from Firestore user profile
 * - benchmarkResult: output of local crypto benchmark
 * - errorMessage: surfaced operational failures
 *
 * Security note:
 * Identity public key is safe to display; private key remains inside Android Keystore.
 */
public class DeveloperViewModel extends ViewModel {

    // LiveData for benchmark summary text
    private final MutableLiveData<String> benchmarkResult = new MutableLiveData<>();

    // LiveData for the Base64-encoded identity public key pulled from Firestore
    private final MutableLiveData<String> identityPublicKey = new MutableLiveData<>();

    // LiveData for displaying any Keystore/Firestore/crypto errors
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Firebase instances for reading user profile
    private final FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public LiveData<String> getBenchmarkResult() {
        return benchmarkResult;
    }

    public LiveData<String> getIdentityPublicKey() {
        return identityPublicKey;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    /**
     * Loads the user's long-term identity public key from Firestore.
     * Private key never leaves the Android Keystore.
     */
    public void loadIdentityPublicKey() {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            errorMessage.setValue("Not logged in");
            return;
        }

        String uid = current.getUid();

        // Fetch user profile: /users/{uid}
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()
                            || task.getResult() == null
                            || !task.getResult().exists()) {
                        errorMessage.setValue("Failed to load user document");
                        return;
                    }

                    // Stored during registration by AuthRepository
                    String key = task.getResult().getString("identityPublicKey");
                    if (key == null) {
                        identityPublicKey.setValue("No identityPublicKey stored for this user.");
                    } else {
                        identityPublicKey.setValue(key);
                    }
                });
    }

    /**
     * Executes local crypto self-test:
     * ECDH (P-256) → HKDF-SHA256 → AES-GCM encryption/decryption roundtrip.
     * No network or Firestore involvement.
     */
    public void runBenchmark() {
        try {
            CryptoBenchmark bench = new CryptoBenchmark();
            String report = bench.runSelfTest();
            benchmarkResult.setValue(report);
        } catch (GeneralSecurityException e) {
            // Surface internal crypto errors
            errorMessage.setValue("Benchmark failed: " + e.getMessage());
        }
    }
}
