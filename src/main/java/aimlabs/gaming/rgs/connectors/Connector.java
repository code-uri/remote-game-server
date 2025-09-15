package aimlabs.gaming.rgs.connectors;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;


@Data
public class Connector extends BaseDto {

    private String uid;
    private String baseUrl;
    private Map<String, Object> settings = new HashMap<>();
    private String network;
    private String parentConnector;
}
