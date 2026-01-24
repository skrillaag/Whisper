package com.whisperwire.app.crypto;

import android.util.Base64;

import com.whisperwire.app.keystore.IdentityKeyStore;

import java.io.IOException;
import java.security.GeneralSecurityException;
import javax.crypto.KeyAgreement;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * Performs ECDH key agreement using the long-term identity keypair.
 *
 * Algorithm choice:
 * - Curve: secp256r1 (NIST P-256)
 * - Security level: ~128-bit
 * - Supported by Android Keystore with hardware-backed storage
 *
 * Usage:
 * - Local private key: identity key from Android Keystore
 * - Remote public key: Base64-encoded X.509 EC public key (stored in Firestore)
 *
 * Output:
 * - Raw shared secret bytes (not directly used as an AES key)
 * - Must be passed through HKDF before use (see HkdfSha256)
 */
public class KeyAgreementManager {

    // I use IdentityKeyStore to load or create the long-term EC identity keypair.
    private final IdentityKeyStore identityKeyStore;

    public KeyAgreementManager() throws GeneralSecurityException, IOException {
        this.identityKeyStore = new IdentityKeyStore();
    }

    /**
     * Derives a raw shared secret with the remote party using ECDH.
     *
     * @param remotePublicKeyBase64 Base64-encoded X.509 EC public key
     * @return raw shared secret bytes (must be fed into HKDF)
     */
    public byte[] deriveSharedSecret(String remotePublicKeyBase64) throws GeneralSecurityException {
        // I always fetch the hardware-backed identity keypair from the keystore.
        KeyPair identityKeyPair = identityKeyStore.getOrCreateIdentityKeyPair();

        // Remote public key is stored in Firestore as Base64-encoded X.509 bytes.
        byte[] remoteBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(remoteBytes);
        PublicKey remotePublicKey = keyFactory.generatePublic(keySpec);

        // Standard ECDH over secp256r1 using the local private identity key.
        KeyAgreement keyAgreement = KeyAgreement.getInstance("ECDH");
        keyAgreement.init(identityKeyPair.getPrivate());
        keyAgreement.doPhase(remotePublicKey, true);

        // Raw, high-entropy secret. Do not use directly as a symmetric key.
        // In WhisperWire this is always expanded via HKDF into AES-GCM keys.
        return keyAgreement.generateSecret();
    }
}
