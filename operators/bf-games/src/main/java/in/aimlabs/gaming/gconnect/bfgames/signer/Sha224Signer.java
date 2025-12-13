package in.aimlabs.gaming.gconnect.bfgames.signer;

import lombok.extern.slf4j.Slf4j;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@Slf4j
public class Sha224Signer {
//    private static final String HEX_ENCODED_EMPTY_STRING_SHA256_HASH = "fb31b105da6f16d285dbfb50ab8b3ddd994111b9be43714c69a1585b02212b85";
    private final MessageDigest sha224Hasher;
   // private final SecretKeySpec secretKeySpec;

    public Sha224Signer(final String secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        sha224Hasher = MessageDigest.getInstance("SHA-224");
       // secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureMethod.HMAC_SHA224);
    }


    /**
     * Hash the given string with sha224
     *
     * @return sha224-hashed message
     */
    public   String hash(final byte[] bytes) {
        if (bytes==null || bytes.length==0) {
            throw new  IllegalArgumentException();
        }
        final byte[] byteData = sha224Hasher.digest(bytes);
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