package com.whisperwire.app.keystore;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

// Stores and manages the device's long-term identity keypair used for ECDH.
// Private key remains inside Android Keystore; only the public key is exported.
public final class IdentityKeyStore {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    // Persistent alias for WhisperWire identity keypair.
    private static final String IDENTITY_KEY_ALIAS = "WW_IDENTITY_KEY";

    // Keystore instance providing hardware-backed or TEE-backed key storage.
    private final KeyStore keyStore;

    public IdentityKeyStore() throws GeneralSecurityException, IOException {
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        // Load the secure keystore; null means use default parameters.
        keyStore.load(null);
    }

    public KeyPair getOrCreateIdentityKeyPair() throws GeneralSecurityException {
        // If no identity keypair exists, I create one using EC P-256.
        if (!keyStore.containsAlias(IDENTITY_KEY_ALIAS)) {
            generateIdentityKeyPair();
        }

        KeyStore.Entry entry = keyStore.getEntry(IDENTITY_KEY_ALIAS, null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new GeneralSecurityException("Identity key entry is not a PrivateKeyEntry");
        }

        // Private key remains non-exportable; public key is retrieved from its certificate.
        PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) entry).getPrivateKey();
        PublicKey publicKey = ((KeyStore.PrivateKeyEntry) entry).getCertificate().getPublicKey();

        return new KeyPair(publicKey, privateKey);
    }

    public String getIdentityPublicKeyBase64() throws GeneralSecurityException {
        // Export public key in Base64 so it can be stored in Firestore and shared with contacts.
        PublicKey publicKey = getOrCreateIdentityKeyPair().getPublic();
        byte[] encoded = publicKey.getEncoded();
        return Base64.encodeToString(encoded, Base64.NO_WRAP);
    }

    private void generateIdentityKeyPair() throws GeneralSecurityException {
        // Generate EC P-256 keypair inside Android Keystore for ECDH key agreement.
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
        );

        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
                IDENTITY_KEY_ALIAS,
                // PURPOSE_AGREE_KEY ensures usage is restricted to ECDH operations.
                KeyProperties.PURPOSE_AGREE_KEY
        )
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                // Key is always available; no biometric/device-auth gating for this project.
                .setUserAuthenticationRequired(false)
                .build();

        // Keypair created directly inside secure hardware; private key is never exportable.
        keyPairGenerator.initialize(keyGenParameterSpec);
        keyPairGenerator.generateKeyPair();
    }
}
