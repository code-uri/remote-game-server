package aimlabs.gaming.rgs.gconnect.slotegrator.client;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for generating HMAC-based signatures and injecting the signature into an Authorization header
 *
 * @author rewolf
 */
@Slf4j
public class Signer {
    private final SecretKeySpec secretKeySpec;

    public Signer(final String secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
		secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * Blocking-client (RestClient/RestTemplate) variant.
     */
    public void injectHeader(final HttpHeaders headers, final byte[] data) {
        final String authHeader = sign(new String(data, StandardCharsets.UTF_8));
		// Keep the Date header valid RFC1123 if present.
		headers.set("Date",  ZonedDateTime.now().toString());
        headers.set("X-SIGN", authHeader);
    }

    /**
     * Sign a string by encode using the HMac based on the secret key generated in the constructor
     *
     * @param stringToSign the message to be signed
     * @return the signature
     */
    private  String sign(final String stringToSign) {
        final byte[] hmacEncode = getMac().doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        //return Arrays.toString(Base64.getEncoder().encode(hmacEncode));
        return String.valueOf(Hex.encode(hmacEncode));
    }

    /**
     * Return a MAC capable of signing our request. Note that it is not thread safe and has state
     * @return the Mac
     */
    private Mac getMac() {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("JDK Does not support the auth scheme", e);
        }
    }

    public static final class Hex {

        private static final char[] HEX = "0123456789abcdef".toCharArray();

        private Hex() {
        }

        public static char[] encode(byte[] bytes) {
            final int nBytes = bytes.length;
            char[] result = new char[2 * nBytes];
            int j = 0;
            for (byte aByte : bytes) {
                // Char for top 4 bits
                result[j++] = HEX[(0xF0 & aByte) >>> 4];
                // Bottom 4
                result[j++] = HEX[(0x0F & aByte)];
            }
            return result;
        }

        public static byte[] decode(CharSequence s) {
            int nChars = s.length();
            if (nChars % 2 != 0) {
                throw new IllegalArgumentException("Hex-encoded string must have an even number of characters");
            }
            byte[] result = new byte[nChars / 2];
            for (int i = 0; i < nChars; i += 2) {
                int msb = Character.digit(s.charAt(i), 16);
                int lsb = Character.digit(s.charAt(i + 1), 16);
                if (msb < 0 || lsb < 0) {
                    throw new IllegalArgumentException(
                            "Detected a Non-hex character at " + (i + 1) + " or " + (i + 2) + " position");
                }
                result[i / 2] = (byte) ((msb << 4) | lsb);
            }
            return result;
        }

    }
}