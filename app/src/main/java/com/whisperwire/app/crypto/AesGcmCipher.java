package com.whisperwire.app.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM utility. For my project I use this as the AEAD layer after HKDF key derivation.
 * It handles authenticated encryption with per-message IVs.
 */
public class AesGcmCipher {

    // AES-GCM with no padding, which I use for all message payloads.
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    // 128-bit tag for integrity and authenticity of ciphertext and AAD.
    private static final int GCM_TAG_BITS = 128;
    // 96-bit IV recommended for GCM; I generate one per encryption.
    private static final int IV_LENGTH_BYTES = 12;

    // SecureRandom for generating IVs so nonces are unpredictable.
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Container for the IV and ciphertext (tag included).
     */
    public static class Ciphertext {
        // The per-message IV that must be stored/transmitted with the ciphertext.
        private final byte[] iv;
        // GCM ciphertext + tag (combined output of doFinal).
        private final byte[] ciphertext;

        public Ciphertext(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }

        public byte[] getIv() {
            return iv;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }
    }

    /**
     * AES-GCM encryption using a 128-bit key derived earlier via HKDF.
     * I allow callers to provide AAD so metadata is authenticated.
     */
    public Ciphertext encrypt(byte[] keyBytes, byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
        // I enforce 16-byte AES keys since all keys in WhisperWire come from HKDF.
        if (keyBytes == null || keyBytes.length != 16) {
            throw new GeneralSecurityException("Invalid AES key length, expected 16 bytes");
        }

        // I generate a fresh IV for this message to avoid nonce reuse.
        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        // GCM appends the tag to the ciphertext automatically.
        byte[] ciphertext = cipher.doFinal(plaintext);
        return new Ciphertext(iv, ciphertext);
    }

    /**
     * AES-GCM decryption. I require the same key and identical AAD;
     * mismatches trigger an authentication failure.
     */
    public byte[] decrypt(byte[] keyBytes, Ciphertext ciphertext, byte[] associatedData) throws GeneralSecurityException {
        // Key size check matches the encryption side.
        if (keyBytes == null || keyBytes.length != 16) {
            throw new GeneralSecurityException("Invalid AES key length, expected 16 bytes");
        }

        // Basic structure check for the ciphertext container.
        if (ciphertext == null || ciphertext.getIv() == null || ciphertext.getCiphertext() == null) {
            throw new GeneralSecurityException("Invalid ciphertext object");
        }

        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, ciphertext.getIv());
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        // AAD must match exactly to verify integrity.
        // If chat/session context is wrong, decryption fails instead of returning corrupted data.
        if (associatedData != null && associatedData.length > 0) {
            cipher.updateAAD(associatedData);
        }

        // GCM throws AEADBadTagException if the tag check fails.
        // WhisperWire treats this as tampering or key/IV/AAD mismatch.
        return cipher.doFinal(ciphertext.getCiphertext());
    }
}
