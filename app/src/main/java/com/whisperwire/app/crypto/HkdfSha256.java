package com.whisperwire.app.crypto;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF implementation using HMAC-SHA256 (RFC 5869).
 *
 * Purpose:
 * - Extract entropy from ECDH shared secret
 * - Derive strong, independent keys for different purposes
 * - Provide key separation via "info" context parameter
 *
 * Typical use in WhisperWire:
 * - Input key material (IKM): ECDH shared secret
 * - Salt: per-session or per-message salt
 * - Info: context string (e.g., "WhisperWire:msg|sessionId|direction")
 * - Output: 16-byte AES-128 key for AES-GCM
 */
public class HkdfSha256 {

    // I fix the MAC to HMAC-SHA256 for both extract and expand phases.
    private static final String HMAC_ALG = "HmacSHA256";
    // Hash output length in bytes for SHA-256.
    private static final int HASH_LEN = 32; // 256-bit

    /**
     * HKDF-Extract and HKDF-Expand.
     *
     * @param ikm         input key material (e.g. shared secret from ECDH)
     * @param salt        optional salt (recommended, can be random or per-session)
     * @param info        optional context string (key separation)
     * @param lengthBytes desired output length in bytes
     */
    public byte[] deriveKey(byte[] ikm, byte[] salt, byte[] info, int lengthBytes) throws GeneralSecurityException {
        // Enforce RFC 5869 bound: at most 255 * HashLen bytes.
        if (lengthBytes <= 0 || lengthBytes > 255 * HASH_LEN) {
            throw new GeneralSecurityException("Invalid HKDF output length");
        }

        // First I run HKDF-Extract to get a pseudorandom key (PRK).
        byte[] prk = extract(salt, ikm);
        // Then I expand that PRK into the requested key length.
        return expand(prk, info, lengthBytes);
    }

    /**
     * Convenience method: derive a 128-bit AES key.
     */
    public byte[] deriveAes128Key(byte[] sharedSecret, byte[] salt, byte[] info) throws GeneralSecurityException {
        // In WhisperWire I standardize on 16-byte keys for AES-GCM-128.
        return deriveKey(sharedSecret, salt, info, 16); // 16 bytes = 128 bits
    }

    private byte[] extract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALG);

        // If salt is not provided, I follow RFC 5869 and use a zero-filled salt of HashLen.
        if (salt == null || salt.length == 0) {
            salt = new byte[HASH_LEN];
        }

        // This step compresses the ECDH secret and salt into a fixed-length PRK.
        mac.init(new SecretKeySpec(salt, HMAC_ALG));
        return mac.doFinal(ikm);
    }

    private byte[] expand(byte[] prk, byte[] info, int lengthBytes) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(prk, HMAC_ALG));

        // Info identifies the context (e.g., message key vs session key) for key separation.
        if (info == null) {
            info = new byte[0];
        }

        int n = (int) Math.ceil((double) lengthBytes / HASH_LEN);
        byte[] result = new byte[lengthBytes];
        byte[] t = new byte[0];
        int offset = 0;

        // I implement the HKDF-Expand loop: T(i) = HMAC(PRK, T(i-1) || info || i).
        for (int i = 1; i <= n; i++) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();

            int bytesToCopy = Math.min(HASH_LEN, lengthBytes - offset);
            System.arraycopy(t, 0, result, offset, bytesToCopy);
            offset += bytesToCopy;
        }

        return result;
    }
}
