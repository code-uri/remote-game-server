package aimlabs.gaming.rgs.players;


import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class Player extends BaseDto {


    private String uid;
    private String firstName;
    private String lastName;
    private String network;
    private String brand;

    public Player(String tenant, String network, String correlationId, String brand, List<String> tags) {
        this.tenant = tenant;
        this.network = network;
        this.correlationId = correlationId;
        this.brand = brand;
        this.tags = tags;
    }
}

