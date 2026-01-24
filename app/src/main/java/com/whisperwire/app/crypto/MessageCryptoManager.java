package com.whisperwire.app.crypto;

import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * High-level message crypto pipeline for WhisperWire.
 *
 * For each message:
 * 1) ECDH (KeyAgreementManager) to derive a raw shared secret with the contact.
 * 2) HKDF-SHA256 (HkdfSha256) to derive a 128-bit AES key from the shared secret.
 *    - salt: per-message random salt
 *    - info: context string including conversationId and messageIndex
 * 3) AES-GCM-128 (AesGcmCipher) to encrypt/decrypt the message.
 *
 * This class hides the low-level details so the rest of the app only deals with:
 * - remote public key (Base64)
 * - plaintext string
 * - Base64-encoded IV / ciphertext / salt
 */
public class MessageCryptoManager {

    // I prefix the HKDF info with an app-specific label to avoid cross-protocol key reuse.
    private static final String HKDF_INFO_PREFIX = "WhisperWire:msg|";

    // High-level components: ECDH, HKDF, and AES-GCM.
    private final KeyAgreementManager keyAgreementManager;
    private final HkdfSha256 hkdfSha256;
    private final AesGcmCipher aesGcmCipher;
    // Used to generate per-message salt for HKDF.
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Holder for encrypted message components as raw bytes.
     * These will later be Base64-encoded for Firestore storage.
     */
    public static class EncryptedMessage {
        private final byte[] iv;
        private final byte[] ciphertext; // includes auth tag
        private final byte[] salt;       // HKDF salt

        public EncryptedMessage(byte[] iv, byte[] ciphertext, byte[] salt) {
            this.iv = iv;
            this.ciphertext = ciphertext;
            this.salt = salt;
        }

        public byte[] getIv() {
            return iv;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }

        public byte[] getSalt() {
            return salt;
        }
    }

    public MessageCryptoManager() throws GeneralSecurityException, IOException {
        // I initialize all crypto primitives once so the rest of the app has a single entry point.
        this.keyAgreementManager = new KeyAgreementManager();
        this.hkdfSha256 = new HkdfSha256();
        this.aesGcmCipher = new AesGcmCipher();
    }

    /**
     * Encrypts a plaintext message for a given remote public key and conversation context.
     *
     * @param remotePublicKeyBase64 Base64-encoded remote identity public key (X.509 EC)
     * @param plaintext             message content
     * @param conversationId        Firestore chat/conversation identifier
     * @param messageIndex          per-conversation message index (or timestamp-based)
     */
    public EncryptedMessage encryptMessage(String remotePublicKeyBase64,
                                           String plaintext,
                                           String conversationId,
                                           long messageIndex) throws GeneralSecurityException {
        // I reject null plaintext early to avoid encrypting invalid inputs.
        if (plaintext == null) {
            throw new GeneralSecurityException("Plaintext is null");
        }

        // 1) ECDH to get raw shared secret
        // Here I use the identity keypair from the keystore plus the remote public key from Firestore.
        byte[] sharedSecret = keyAgreementManager.deriveSharedSecret(remotePublicKeyBase64);

        // 2) Build HKDF salt (random per message) and info (context)
        byte[] salt = new byte[16]; // 128-bit salt per message
        secureRandom.nextBytes(salt); // Per-message salt ensures key separation even for same shared secret.

        String infoString = HKDF_INFO_PREFIX + conversationId + "|" + messageIndex;
        byte[] info = infoString.getBytes(StandardCharsets.UTF_8); // Info binds the key to a specific conversation and message.

        // 3) Derive AES-128 key via HKDF
        byte[] aesKey = hkdfSha256.deriveAes128Key(sharedSecret, salt, info);

        // 4) AES-GCM encryption (associatedData can bind conversationId, etc.)
        // I use conversationId as AAD so ciphertext is tied to the correct chat document.
        byte[] associatedData = conversationId.getBytes(StandardCharsets.UTF_8);

        AesGcmCipher.Ciphertext cipherResult = aesGcmCipher.encrypt(
                aesKey,
                plaintext.getBytes(StandardCharsets.UTF_8),
                associatedData
        );

        // I return the raw components; encoding is handled separately for Firestore storage.
        return new EncryptedMessage(
                cipherResult.getIv(),
                cipherResult.getCiphertext(),
                salt
        );
    }

    /**
     * Decrypts a message using the same remote public key and context.
     *
     * @param remotePublicKeyBase64 remote identity public key (Base64)
     * @param iv                    IV used for AES-GCM
     * @param ciphertext            ciphertext (includes auth tag)
     * @param salt                  HKDF salt used when encrypting
     * @param conversationId        conversation identifier
     * @param messageIndex          same index used at encryption time
     * @return plaintext message as String
     */
    public String decryptMessage(String remotePublicKeyBase64,
                                 byte[] iv,
                                 byte[] ciphertext,
                                 byte[] salt,
                                 String conversationId,
                                 long messageIndex) throws GeneralSecurityException {

        // I require all crypto parameters to be present before attempting decryption.
        if (iv == null || ciphertext == null || salt == null) {
            throw new GeneralSecurityException("Missing IV, ciphertext, or salt");
        }

        // 1) ECDH shared secret
        // I recompute the same shared secret from the identity key and remote public key.
        byte[] sharedSecret = keyAgreementManager.deriveSharedSecret(remotePublicKeyBase64);

        // 2) Rebuild HKDF info (must match encryption)
        String infoString = HKDF_INFO_PREFIX + conversationId + "|" + messageIndex;
        byte[] info = infoString.getBytes(StandardCharsets.UTF_8);

        // 3) Derive AES-128 key
        // Using the same sharedSecret, salt, and info guarantees the same symmetric key.
        byte[] aesKey = hkdfSha256.deriveAes128Key(sharedSecret, salt, info);

        // 4) AES-GCM decryption
        // I reuse conversationId as AAD, so any mismatch will cause an auth failure.
        byte[] associatedData = conversationId.getBytes(StandardCharsets.UTF_8);

        AesGcmCipher.Ciphertext cipherObj = new AesGcmCipher.Ciphertext(iv, ciphertext);
        byte[] plaintextBytes = aesGcmCipher.decrypt(aesKey, cipherObj, associatedData);

        return new String(plaintextBytes, StandardCharsets.UTF_8);
    }

    /**
     * Helper to Base64-encode an EncryptedMessage for Firestore storage.
     */
    public static class EncryptedMessageEncoded {
        public final String ivBase64;
        public final String ciphertextBase64;
        public final String saltBase64;

        public EncryptedMessageEncoded(String ivBase64, String ciphertextBase64, String saltBase64) {
            this.ivBase64 = ivBase64;
            this.ciphertextBase64 = ciphertextBase64;
            this.saltBase64 = saltBase64;
        }
    }

    public EncryptedMessageEncoded encodeForStorage(EncryptedMessage message) {
        // I encode all binary fields as Base64 strings so they can be stored safely in Firestore.
        String ivB64 = Base64.encodeToString(message.getIv(), Base64.NO_WRAP);
        String ctB64 = Base64.encodeToString(message.getCiphertext(), Base64.NO_WRAP);
        String saltB64 = Base64.encodeToString(message.getSalt(), Base64.NO_WRAP);

        return new EncryptedMessageEncoded(ivB64, ctB64, saltB64);
    }

    public EncryptedMessage decodeFromStorage(String ivBase64, String ciphertextBase64, String saltBase64) {
        // When reading from Firestore I reverse the Base64 encoding back into raw bytes.
        byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
        byte[] ct = Base64.decode(ciphertextBase64, Base64.NO_WRAP);
        byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
        return new EncryptedMessage(iv, ct, salt);
    }
}
