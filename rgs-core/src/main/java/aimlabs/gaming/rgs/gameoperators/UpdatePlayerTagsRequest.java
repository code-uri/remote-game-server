package aimlabs.gaming.rgs.gameoperators;

import lombok.Data;

import java.util.List;

@Data
public class UpdatePlayerTagsRequest {
    String clientId;

    String brand;

    String player;

    List<String> tags;
}
