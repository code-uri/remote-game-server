package aimlabs.gaming.rgs.gamerounds;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class UnfinishedGame {

    GameRound gameRound;
    JsonNode gamePlay;
    JsonNode gameActivity;
}
