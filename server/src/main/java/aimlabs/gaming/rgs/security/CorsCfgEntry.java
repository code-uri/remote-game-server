package aimlabs.gaming.rgs.security;

import lombok.Data;

import java.util.StringJoiner;

@Data
public class CorsCfgEntry {

    String path;
    String allowedOrigins = "http://localhost:[8081,9000,3000],http://127.0.0.1:[8081,9000,3000]";
    String allowedMethods = "GET,POST";
    String allowedHeaders = "*";
    String exposedHeaders = "*";


    @Override
    public String toString() {
        return new StringJoiner(", ", CorsCfgEntry.class.getSimpleName() + "[", "]")
                .add("path='" + path + "'")
                .add("allowedOrigins='" + allowedOrigins + "'")
                .add("allowedMethods='" + allowedMethods + "'")
                .add("allowedHeaders='" + allowedHeaders + "'")
                .add("exposedHeaders='" + exposedHeaders + "'")
                .toString();
    }
}
