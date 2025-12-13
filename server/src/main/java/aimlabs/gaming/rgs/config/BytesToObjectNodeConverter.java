package aimlabs.gaming.rgs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.stereotype.Component;

@ReadingConverter
@Component
public class BytesToObjectNodeConverter implements Converter<byte[], ObjectNode> {

  private final Jackson2JsonRedisSerializer<ObjectNode> serializer;

  public BytesToObjectNodeConverter(ObjectMapper objectMapper) {

    serializer = new Jackson2JsonRedisSerializer<ObjectNode>(objectMapper, ObjectNode.class);
  }

  
  public ObjectNode convert(byte[] value) {
    return serializer.deserialize(value);
  }
}