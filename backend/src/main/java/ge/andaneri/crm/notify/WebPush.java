package ge.andaneri.crm.notify;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The two pieces of Web Push that need cryptography, done with the JDK alone:
 * <ul>
 * <li>RFC 8291 / RFC 8188: encrypting the message ("aes128gcm") so only the receiving device can read it;</li>
 * <li>RFC 8292 (VAPID): a signed token saying which server is sending, so push services accept it.</li>
 * </ul>
 * All keys are P-256; public keys travel as uncompressed points (65 bytes) in base64url.
 */
public final class WebPush {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final int RECORD_SIZE = 4096;

    private WebPush() {
    }

    // ---------------------------------------------------------------- encryption (aes128gcm)

    /** The request body for one push message: salt, record size, the sender's one-time public key, ciphertext. */
    public static byte[] encrypt(byte[] payload, String receiverPublicKey, String authSecret) throws GeneralSecurityException {
        ECPublicKey receiver = publicKey(decode(receiverPublicKey));
        byte[] receiverPoint = point(receiver);
        byte[] auth = decode(authSecret);

        KeyPair ephemeral = newKeyPair();
        byte[] senderPoint = point((ECPublicKey) ephemeral.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(ephemeral.getPrivate());
        agreement.doPhase(receiver, true);
        byte[] shared = agreement.generateSecret();

        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiverPoint, senderPoint);
        byte[] ikm = hkdf(auth, shared, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // One record, marked as the last one (0x02), no padding.
        byte[] plain = Arrays.copyOf(payload, payload.length + 1);
        plain[payload.length] = 2;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(plain);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + senderPoint.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) senderPoint.length).put(senderPoint);
        return concat(header.array(), ciphertext);
    }

    /** HKDF (RFC 5869) with SHA-256, for outputs of at most one block (32 bytes), which is all Web Push needs. */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] block = hmac(prk, concat(info, new byte[] {1}));
        return Arrays.copyOf(block, length);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // ---------------------------------------------------------------- VAPID

    /** The Authorization header value for a push to {@code endpoint}, valid for 12 hours. */
    public static String vapidAuthorization(String endpoint, KeyPair vapid, String subject, Instant now) throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + (now.getEpochSecond() + 12 * 3600)
                + ",\"sub\":\"" + subject.replace("\"", "") + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(vapid.getPrivate());
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        String token = header + "." + claims + "." + B64.encodeToString(signer.sign());
        return "vapid t=" + token + ", k=" + encode(point((ECPublicKey) vapid.getPublic()));
    }

    // ---------------------------------------------------------------- keys

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
        return generator.generateKeyPair();
    }

    /** A key pair from its base64url public point and private scalar, as web-push tools print them. */
    public static KeyPair keyPair(String publicKey, String privateKey) throws GeneralSecurityException {
        ECPublicKey pub = publicKey(decode(publicKey));
        ECPrivateKey priv = (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, decode(privateKey)), curve()));
        return new KeyPair(pub, priv);
    }

    public static String publicKeyText(KeyPair pair) {
        return encode(point((ECPublicKey) pair.getPublic()));
    }

    public static String privateKeyText(KeyPair pair) {
        return encode(unsigned(((ECPrivateKey) pair.getPrivate()).getS(), 32));
    }

    static ECPublicKey publicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 point");
        }
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, curve()));
    }

    static byte[] point(ECPublicKey key) {
        return concat(new byte[] {4}, unsigned(key.getW().getAffineX(), 32), unsigned(key.getW().getAffineY(), 32));
    }

    static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] unsigned(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        byte[] out = new byte[length];
        int copy = Math.min(bytes.length, length);
        System.arraycopy(bytes, bytes.length - copy, out, length - copy, copy);
        return out;
    }

    public static byte[] decode(String base64url) {
        return B64D.decode(base64url.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    public static String encode(byte[] bytes) {
        return B64.encodeToString(bytes);
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
