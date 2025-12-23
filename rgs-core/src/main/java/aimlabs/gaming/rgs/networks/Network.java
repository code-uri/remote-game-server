package aimlabs.gaming.rgs.networks;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.List;

@Data
public class Network extends BaseDto {

    String uid;
    String name;
    String clientId;
    String clientKey;
    List<String> connectors;
    List<String> settings;
}
