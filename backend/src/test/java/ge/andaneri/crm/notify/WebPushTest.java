package ge.andaneri.crm.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * The message a device receives decrypts, following RFC 8291 from the receiving side, and the VAPID token
 * verifies with the server's public key. Together with a real browser test this is what push services check.
 */
class WebPushTest {

    @Test
    void aDeviceCanDecryptWhatIsSent() throws Exception {
        KeyPair device = WebPush.newKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String message = "{\"title\":\"17:00 · შეხვედრა · Bar X\",\"body\":\"Giorgi · +995 555 00 00 01\"}";

        byte[] body = WebPush.encrypt(message.getBytes(StandardCharsets.UTF_8),
                WebPush.encode(WebPush.point((ECPublicKey) device.getPublic())), WebPush.encode(auth));

        assertThat(new String(decrypt(body, device, auth), StandardCharsets.UTF_8)).isEqualTo(message);
    }

    @Test
    void theVapidTokenIsSignedForThePushServiceItGoesTo() throws Exception {
        KeyPair server = WebPush.newKeyPair();
        String header = WebPush.vapidAuthorization("https://fcm.googleapis.com/fcm/send/abc123", server, "mailto:crm@andaneri.ge",
                Instant.parse("2026-09-15T10:00:00Z"));

        assertThat(header).startsWith("vapid t=").contains(", k=" + WebPush.publicKeyText(server));
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"sub\":\"mailto:crm@andaneri.ge\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    void keysSurviveBeingWrittenOutAndReadBack() throws Exception {
        KeyPair original = WebPush.newKeyPair();
        KeyPair restored = WebPush.keyPair(WebPush.publicKeyText(original), WebPush.privateKeyText(original));

        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(restored.getPrivate());
        signer.update(new byte[] {1, 2, 3});
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(original.getPublic());
        verifier.update(new byte[] {1, 2, 3});
        assertThat(verifier.verify(signer.sign())).isTrue();
        assertThat(WebPush.decode(WebPush.publicKeyText(restored))).hasSize(65);
    }

    @Test
    void onlyRealPushServicesAreAccepted() {
        assertThat(PushService.isPushServiceAddress("https://fcm.googleapis.com/fcm/send/x")).isTrue();
        assertThat(PushService.isPushServiceAddress("https://web.push.apple.com/QGx")).isTrue();
        assertThat(PushService.isPushServiceAddress("https://updates.push.services.mozilla.com/wpush/v2/x")).isTrue();
        assertThat(PushService.isPushServiceAddress("https://wns2-db5p.notify.windows.com/w/?token=x")).isTrue();
        assertThat(PushService.isPushServiceAddress("http://fcm.googleapis.com/x")).isFalse();
        assertThat(PushService.isPushServiceAddress("https://mysql.railway.internal/x")).isFalse();
        assertThat(PushService.isPushServiceAddress("https://evilfcm.googleapis.com.example.org/x")).isFalse();
        assertThat(PushService.isPushServiceAddress("not a url")).isFalse();
    }

    /** RFC 8291 decryption, as a browser does it. */
    private static byte[] decrypt(byte[] body, KeyPair device, byte[] auth) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        assertThat(ByteBuffer.wrap(body, 16, 4).getInt()).isEqualTo(4096);
        int idLength = body[20];
        byte[] senderPoint = Arrays.copyOfRange(body, 21, 21 + idLength);
        byte[] ciphertext = Arrays.copyOfRange(body, 21 + idLength, body.length);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(device.getPrivate());
        agreement.doPhase(WebPush.publicKey(senderPoint), true);
        byte[] shared = agreement.generateSecret();

        byte[] keyInfo = WebPush.concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                WebPush.point((ECPublicKey) device.getPublic()), senderPoint);
        byte[] ikm = WebPush.hkdf(auth, shared, keyInfo, 32);
        byte[] cek = WebPush.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPush.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(ciphertext);
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plain, plain.length - 1);
    }
}
