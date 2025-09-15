package aimlabs.gaming.rgs.core.utils.json;

import com.fasterxml.jackson.databind.JsonNode;

public class JsonNodeUtils {

    public static String asText(JsonNode node) {
        return node != null ? node.asText() : null;
    }

    public static Boolean asBoolean(JsonNode node) {
        return node != null ? node.asBoolean() : null;
    }
}