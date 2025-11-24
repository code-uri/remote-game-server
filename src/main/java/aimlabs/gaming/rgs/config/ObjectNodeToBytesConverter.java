package aimlabs.gaming.rgs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class ObjectNodeToBytesConverter implements Converter<ObjectNode, byte[]> {

  private final Jackson2JsonRedisSerializer<ObjectNode> serializer;

  public ObjectNodeToBytesConverter(ObjectMapper objectMapper) {

    serializer = new Jackson2JsonRedisSerializer<ObjectNode>(objectMapper, ObjectNode.class);
  }

  
  public byte[] convert(ObjectNode value) {
    return serializer.serialize(value);
  }
}
