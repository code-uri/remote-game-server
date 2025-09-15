package aimlabs.gaming.rgs.core.utils;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class UUIDShortener {


    public static String msblsbHexFromUUID(UUID uuid){
        // Convert to a hexadecimal string
        String msbHex = Long.toHexString(uuid.getMostSignificantBits());
        String lsbHex = Long.toHexString(uuid.getLeastSignificantBits());


        // Ensure the string is 16 characters long, padded with zeros if needed
        return msbHex.concat(lsbHex);
    }

    public static UUID reconstructUUID(String msbHex, String lsbHex) {
        // Convert hexadecimal strings to long values
        long msb = Long.parseUnsignedLong(msbHex, 16);
        long lsb = Long.parseUnsignedLong(lsbHex, 16);

        // Create a new UUID using the MSB and LSB
        return new UUID(msb, lsb);
    }

    public static UUID reconstructUUID(String withoutHyphenated) {


        // Convert hexadecimal strings to long values
        long msb = Long.parseUnsignedLong(withoutHyphenated.substring(0,16), 16);
        long lsb = Long.parseUnsignedLong(withoutHyphenated.substring(16,32), 16);

        // Create a new UUID using the MSB and LSB
        return new UUID(msb, lsb);
    }

    // Method to shorten UUID
    public static String shortenUUID() {
        UUID uuid = UUID.randomUUID();
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    }

    public static String shortenUUID(UUID uuid) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[16]);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
    }

    // Method to decode shortened UUID back to standard UUID
    public static UUID decodeShortenedUUID(String shortUUID) {
        byte[] bytes = Base64.getUrlDecoder().decode(shortUUID);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long mostSigBits = byteBuffer.getLong();
        long leastSigBits = byteBuffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }


    public static void main(String[] args) {
        System.out.println(UUIDShortener.decodeShortenedUUID("C4zXPS2UTpuaVHsIWeh37w"));
    }
}
