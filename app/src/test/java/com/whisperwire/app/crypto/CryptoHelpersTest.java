package com.whisperwire.app.crypto;

import org.junit.Test;

import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

/**
 * Unit tests for core crypto helpers used by WhisperWire.
 * These run on the JVM (no Android dependencies).
 */
public class CryptoHelpersTest {

    private final SecureRandom secureRandom = new SecureRandom();

    // ---------- HKDF (HkdfSha256) ----------

    @Test
    public void hkdf_isDeterministic() throws Exception {
        HkdfSha256 hkdf = new HkdfSha256();

        byte[] sharedSecret = new byte[32];
        secureRandom.nextBytes(sharedSecret);

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);

        byte[] info = "WhisperWireTest".getBytes();

        byte[] k1 = hkdf.deriveAes128Key(sharedSecret, salt, info);
        byte[] k2 = hkdf.deriveAes128Key(sharedSecret, salt, info);

        assertArrayEquals("HKDF must be deterministic for same inputs", k1, k2);
    }

    @Test
    public void hkdf_changesWithInfo() throws Exception {
        HkdfSha256 hkdf = new HkdfSha256();

        byte[] sharedSecret = new byte[32];
        secureRandom.nextBytes(sharedSecret);

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);

        byte[] info1 = "info-1".getBytes();
        byte[] info2 = "info-2".getBytes();

        byte[] k1 = hkdf.deriveAes128Key(sharedSecret, salt, info1);
        byte[] k2 = hkdf.deriveAes128Key(sharedSecret, salt, info2);

        // compare as hex strings so assertNotEquals works reliably
        assertNotEquals("Different info should give different keys",
                bytesToHex(k1), bytesToHex(k2));
    }

    // ---------- AES-GCM (AesGcmCipher) ----------

    @Test
    public void aesGcm_roundTrip() throws Exception {
        AesGcmCipher cipher = new AesGcmCipher();

        byte[] key = new byte[16]; // AES-128
        secureRandom.nextBytes(key);

        String message = "This is a test message for AES-GCM.";
        byte[] plaintext = message.getBytes();

        AesGcmCipher.Ciphertext ct = cipher.encrypt(key, plaintext, null);
        byte[] decrypted = cipher.decrypt(key, ct, null);

        assertArrayEquals("AES-GCM decrypt should recover original plaintext",
                plaintext, decrypted);
    }

    @Test
    public void aesGcm_detectsTampering() throws Exception {
        AesGcmCipher cipher = new AesGcmCipher();

        byte[] key = new byte[16];
        secureRandom.nextBytes(key);

        byte[] plaintext = "Tamper test".getBytes();

        AesGcmCipher.Ciphertext ct = cipher.encrypt(key, plaintext, null);

        // flip one bit in ciphertext to simulate tampering
        byte[] ctBytes = ct.getCiphertext();
        ctBytes[0] ^= 0x01;

        try {
            cipher.decrypt(key, ct, null);
            fail("Expected decryption to fail due to tampered ciphertext");
        } catch (Exception expected) {
            // ok: AES-GCM must reject modified ciphertext
        }
    }

    // ---------- ECDH (secp256r1) ----------

    @Test
    public void ecdh_sharedSecretMatches() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));

        KeyPair a = kpg.generateKeyPair();
        KeyPair b = kpg.generateKeyPair();

        KeyAgreement kaA = KeyAgreement.getInstance("ECDH");
        kaA.init(a.getPrivate());
        kaA.doPhase(b.getPublic(), true);
        byte[] sharedA = kaA.generateSecret();

        KeyAgreement kaB = KeyAgreement.getInstance("ECDH");
        kaB.init(b.getPrivate());
        kaB.doPhase(a.getPublic(), true);
        byte[] sharedB = kaB.generateSecret();

        assertArrayEquals("ECDH shared secrets must match", sharedA, sharedB);
        assertEquals("P-256 shared secret length should be 32 bytes",
                32, sharedA.length);
    }

    // ---------- helpers ----------

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
