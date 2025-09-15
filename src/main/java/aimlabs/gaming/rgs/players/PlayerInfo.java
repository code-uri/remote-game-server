package aimlabs.gaming.rgs.players;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PlayerInfo {
    String uid;
    String player;
    PlayerWallet wallet;
    Map<String, Object> settings = new HashMap<>();
    String externalToken;
    List<String> tags;
    private boolean supportsMultiCredits = true;
}
