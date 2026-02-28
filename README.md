# Whisper
Whisper is an Android messaging application that implements secure, end-to-end encrypted communication. All encryption is performed locally on the device using ECDH, HKDF-SHA256, and AES-GCM. Firebase is used for authentication and real-time data synchronization. This project was developed as part of the COS Senior Project Fall 2025.

## Features:
- End-to-end encrypted text messaging
- Identity keys generated and stored using Android Keystore
- Contact requests (send, accept, reject)
- Contacts list and unread indicators
- Real-time chat updates through Firestore snapshot listeners
- Typing indicator
- Safety number for manual identity verification
- Full account deletion with recursive Firestore cleanup
- Developer Mode with a cryptographic benchmark

## Security

All encryption and key derivation operations are performed locally on the device.
Firebase is used only for authentication and real-time data synchronization and does not have access to plaintext message content.

Cryptographic components used:
- ECDH (Elliptic Curve Diffie–Hellman) for key exchange
- HKDF-SHA256 for key derivation
- AES-GCM for authenticated encryption

## Installation Requirements:

Android Studio (current release)
Android SDK (installed automatically with Android Studio)
Android device or emulator running Android 7.0 (API 24) or higher

Steps:

1. Clone or download the project repository.
2. Open the project folder in Android Studio.
3. Allow Gradle to complete synchronization.
4. Connect an Android device with USB debugging enabled or start an emulator.
5. Select the target device and run the project from Android Studio.
6. Register a new account to begin using WhisperWire.

## Screenshots
Screenshots of the application can be found in screenshots/ directory.

## Contact
For further information:
    Altangerel Dashtseren
    email: skrillaag@gmail.com
