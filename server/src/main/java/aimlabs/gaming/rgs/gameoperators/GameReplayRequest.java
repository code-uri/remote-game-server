package aimlabs.gaming.rgs.gameoperators;

import lombok.Data;

@Data
public class GameReplayRequest {

    String gameRound;

    String playerId;

    String gameSession;

}
