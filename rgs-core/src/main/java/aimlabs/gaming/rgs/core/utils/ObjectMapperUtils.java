package aimlabs.gaming.rgs.core.utils;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class ObjectMapperUtils {

    public static JsonNode convertToJsonNode(ObjectMapper objectMapper, Object object){
        try {
            JsonNode jsonObject = objectMapper.readTree(objectMapper.writeValueAsString(object));
            if(!jsonObject.isObject()){
                throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Not an object error!");
            }
           return jsonObject;
        } catch (JsonProcessingException e) {
            log.error("Failed to convert ", e);
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    public static Map<String, Object> convertToMap(ObjectMapper objectMapper, JsonNode jsonNode) {
       // try {
            Map<String, Object> map = objectMapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
            });

            return map;
      /*  } catch (JsonProcessingException e) {
            log.error("Failed to convert ", e);
            return Mono.error(new SystemRuntimeException(SystemErrorCode.SYSTEM_ERROR, e.getMessage()));
        }*/
    }
}
