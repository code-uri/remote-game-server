package in.aimlabs.gaming.gconnect.softsiss.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.crypto.dsig.SignatureMethod;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;

/**
 * Utility class for generating HMAC-based signatures and injecting the signature into an Authorization header
 *
 * @author rewolf
 */
@Slf4j
public class Signer {
    private static final String HEX_ENCODED_EMPTY_STRING_SHA256_HASH = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private final MessageDigest sha256Hasher;
    private final SecretKeySpec secretKeySpec;

    public Signer(final String secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        sha256Hasher = MessageDigest.getInstance("SHA-256");
        secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureMethod.HMAC_SHA256);
    }

    public void injectHeader(final ClientHttpRequest clientRequest, final byte[] data) {
        //log.debug("injectHeader for: [" + clientRequest.getURI() +"], data=" + new String(data) );
        final String dateString = ZonedDateTime.now().toString();
        final String authHeader = buildAuthHeaderForRequest(
                clientRequest,
                dateString,
                data);

        clientRequest.getHeaders().add(HttpHeaders.DATE, dateString);
        clientRequest.getHeaders().add("X-REQUEST-SIGN", authHeader);
    }

    public void injectHeader(final ServerHttpRequest serverHttpRequest, final ServerHttpResponse serverHttpResponse, final byte[] data) {
        //log.info("injectHeader for: [" + serverHttpRequest.getURI() +"], data=" + new String(data) );
        final String dateString = ZonedDateTime.now().toString();
        final String authHeader = buildAuthHeaderForRequest(
                serverHttpRequest,
                dateString,
                data);

        //serverHttpResponse.getHeaders().add(HttpHeaders.DATE, dateString);
        serverHttpResponse.getHeaders().set("X-Request-Sign", authHeader);
    }

    /**
     * Build the Authorization header value for the given request
     *
     * @param clientHttpRequest the request from which to pull fields for signing
     * @param dateHeaderValue date string used in date header, for signing
     * @param body the byte data for the body, for signing
     * @return the Authorization header value including the signature
     */
    private String buildAuthHeaderForRequest(final ClientHttpRequest clientHttpRequest,
                                             final String dateHeaderValue,
                                             final byte[] body) {
        String strToSign = new String(body);
        //log.info("\nString-to-sign:\n----\n{}\n----------", strToSign);
        return sign(strToSign);
    }

    /**
     * Build the Authorization header value for the given request
     *
     * @param serverHttpRequest the request from which to pull fields for signing
     * @param dateHeaderValue date string used in date header, for signing
     * @param body the byte data for the body, for signing
     * @return the Authorization header value including the signature
     */
    private String buildAuthHeaderForRequest(final ServerHttpRequest serverHttpRequest,
                                             final String dateHeaderValue,
                                             final byte[] body) {
        String strToSign = new String(body);
        // log.info("\nString-to-sign:\n----\n{}\n----------", strToSign);
        return sign(strToSign);
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

    /**
     * Hash the given string with sha256
     *
     * @return sha256-hashed message
     */
    private  String hash(final byte[] bytes) {
        if (bytes==null || bytes.length==0) {
            return HEX_ENCODED_EMPTY_STRING_SHA256_HASH;
        }
        final byte[] byteData = sha256Hasher.digest(bytes);
        return String.valueOf(Hex.encode(byteData));
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