# Privacy Policy for TouchDeck

**Last Updated:** October 19, 2026

TouchDeck ("the App", "we", "us", or "our") is designed to turn your Android device into a wireless controller, trackpad, and keyboard for your personal computer. We respect your privacy and are committed to protecting it.

## 1. Information Collection and Use

**TouchDeck does NOT collect, store, sell, or transmit any personal data to external servers or third parties.**

- **Local Network Only:** All communication occurs exclusively over your local Wi-Fi / Local Area Network (LAN) between your mobile device and your personal computer running the TouchDeck Companion software.
- **No Cloud Tracking:** The App contains no tracking SDKs, no advertisement networks, no third-party analytics, and no cloud backends.
- **Input Data:** Mouse movements, keyboard inputs, and gamepad actions are sent directly and in real-time to your paired PC companion. No input logs or keystroke histories are recorded or transmitted off your device.

## 2. Permissions Used by TouchDeck

The App requests only the minimum permissions necessary for wireless control and tactile feedback:

- **`android.permission.INTERNET`**: Required to establish local WebSocket connections to the TouchDeck Companion on your PC.
- **`android.permission.ACCESS_NETWORK_STATE` & `ACCESS_WIFI_STATE`**: Used strictly to detect local Wi-Fi connectivity and retrieve your local IP address for pairing.
- **`android.permission.VIBRATE`**: Used to deliver haptic feedback when pressing virtual controller buttons and keys.

## 3. Data Security

- **Credential Storage:** Pairing tokens and device identifiers are stored locally using hardware-backed cryptographic security via `EncryptedSharedPreferences` (AES-256-GCM) managed by the Android Keystore.
- **Challenge-Response Authentication:** Returning connections authenticate using HMAC-SHA256 challenge-response, ensuring authentication tokens are never transmitted in cleartext over the network.
- **No Data Retention:** When you click "Forget Paired PC" in settings, all paired credentials and active sessions are wiped immediately from your device.

## 4. Children’s Privacy

TouchDeck does not collect any personal information from anyone, including children under the age of 13.

## 5. Changes to This Privacy Policy

If we update this Privacy Policy, the revised policy will be posted within the application and in our open-source repository.

## 6. Contact Us

If you have any questions or suggestions regarding this Privacy Policy, please open an issue on our GitHub repository:
https://github.com/TouchDeck/TouchDeck/issues
