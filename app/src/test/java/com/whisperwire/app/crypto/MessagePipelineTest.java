package com.whisperwire.app.crypto;

import org.junit.Test;

import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Integration-style tests for the WhisperWire message crypto pipeline:
 * ECDH -> HKDF (HMAC-SHA256) -> AES-GCM.
 */
public class MessagePipelineTest {

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Simulates a single WhisperWire message:
     * - Alice and Bob derive a shared secret via ECDH
     * - Both derive the same AES key with HKDF using chatId + messageIndex
     * - Alice encrypts, Bob decrypts successfully
     */
    @Test
    public void singleMessageRoundTrip() throws Exception {
        // 1) Generate identity key pairs for Alice and Bob (secp256r1)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));

        KeyPair alice = kpg.generateKeyPair();
        KeyPair bob = kpg.generateKeyPair();

        // 2) ECDH: both sides derive the same shared secret
        byte[] sharedAlice = deriveSharedSecret(alice, bob);
        byte[] sharedBob = deriveSharedSecret(bob, alice);

        assertArrayEquals("ECDH shared secrets must match", sharedAlice, sharedBob);

        // 3) HKDF: derive per-message AES key using chatId + messageIndex as context
        HkdfSha256 hkdf = new HkdfSha256();

        String chatId = "test_chat_123";
        long messageIndex = 1L;

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);

        byte[] info = buildInfo(chatId, messageIndex);

        byte[] aliceKey = hkdf.deriveAes128Key(sharedAlice, salt, info);
        byte[] bobKey = hkdf.deriveAes128Key(sharedBob, salt, info);

        assertArrayEquals("Both parties must derive the same message key", aliceKey, bobKey);

        // 4) AES-GCM: Alice encrypts, Bob decrypts
        AesGcmCipher cipher = new AesGcmCipher();

        String message = "Hello from Alice to Bob over WhisperWire!";
        byte[] plaintext = message.getBytes();

        // No additional authenticated data for this test (AAD = null)
        AesGcmCipher.Ciphertext ct = cipher.encrypt(aliceKey, plaintext, null);

        byte[] decrypted = cipher.decrypt(bobKey, ct, null);

        assertArrayEquals("Decrypted message must match original plaintext",
                plaintext, decrypted);
    }

    /**
     * Ensures that two different messages (different messageIndex) use different AES keys
     * and produce different ciphertexts, even with the same shared secret and chatId.
     */
    @Test
    public void differentMessagesUseDifferentKeysAndCiphertexts() throws Exception {
        // Shared identity for both messages
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));

        KeyPair alice = kpg.generateKeyPair();
        KeyPair bob = kpg.generateKeyPair();

        byte[] shared = deriveSharedSecret(alice, bob);

        HkdfSha256 hkdf = new HkdfSha256();
        AesGcmCipher cipher = new AesGcmCipher();

        String chatId = "test_chat_456";

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);

        String message = "Per-message key separation test";
        byte[] plaintext = message.getBytes();

        // Message 1
        long index1 = 1L;
        byte[] info1 = buildInfo(chatId, index1);
        byte[] key1 = hkdf.deriveAes128Key(shared, salt, info1);
        AesGcmCipher.Ciphertext ct1 = cipher.encrypt(key1, plaintext, null);

        // Message 2 (different index)
        long index2 = 2L;
        byte[] info2 = buildInfo(chatId, index2);
        byte[] key2 = hkdf.deriveAes128Key(shared, salt, info2);
        AesGcmCipher.Ciphertext ct2 = cipher.encrypt(key2, plaintext, null);

        // Keys should differ
        assertNotEquals("Per-message keys must be different",
                bytesToHex(key1), bytesToHex(key2));

        // Ciphertexts should differ too
        assertNotEquals("Per-message ciphertexts should be different",
                bytesToHex(ct1.getCiphertext()), bytesToHex(ct2.getCiphertext()));
    }

    // ----- helpers -----

    private byte[] deriveSharedSecret(KeyPair self, KeyPair other) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(self.getPrivate());
        ka.doPhase(other.getPublic(), true);
        return ka.generateSecret();
    }

    private byte[] buildInfo(String chatId, long messageIndex) {
        // Simple info = "chatId|index" as bytes
        String infoStr = chatId + "|" + messageIndex;
        return infoStr.getBytes();
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
