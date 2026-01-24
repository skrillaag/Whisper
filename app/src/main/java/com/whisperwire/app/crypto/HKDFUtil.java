package com.whisperwire.app.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HKDFUtil {

    // I reuse the same HMAC-SHA256 primitive as in the main HKDF implementation.
    private static final String HMAC_ALG = "HmacSHA256";

    // HKDF-Extract implemented as a simple HMAC helper.
    private byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(key, HMAC_ALG));
        return mac.doFinal(data);
    }

    /**
     * HKDF (HMAC-SHA256) expand function.
     * @param ikm Input keying material (shared secret)
     * @param info Context string (e.g. "chatId", "benchmark-test")
     * @param length Number of bytes to produce (e.g. 16 for AES-128)
     */
    public byte[] deriveKey(byte[] ikm, byte[] info, int length) throws Exception {
        // I perform a minimal HKDF-Extract using a zero salt (32-byte zero array).
        byte[] prk = hmac(new byte[32], ikm); // Extract using salt = zero array
        // okm will hold the final output keying material.
        byte[] okm = new byte[length];

        // previous corresponds to T(n-1) in HKDF-Expand.
        byte[] previous = new byte[0];
        int pos = 0;
        int counter = 1;

        while (pos < length) {
            // T(n) = HMAC(PRK, T(n-1) | info | counter)
            byte[] input = new byte[previous.length + info.length + 1];

            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(info, 0, input, previous.length, info.length);
            input[input.length - 1] = (byte) counter;

            // I compute the next block in the HKDF-Expand chain.
            previous = hmac(prk, input);

            int remaining = length - pos;
            int toCopy = Math.min(remaining, previous.length);
            System.arraycopy(previous, 0, okm, pos, toCopy);
            pos += toCopy;

            counter++;
        }

        // Caller can use this output as an AES key or other symmetric key material.
        return okm;
    }
}
