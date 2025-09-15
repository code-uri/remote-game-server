package aimlabs.gaming.rgs.games;

import com.fasterxml.jackson.databind.JsonNode;

public interface GameHandler {
    void handle(JsonNode request, GamePlayContext context);
    void setNext(GameHandler nextHandler);
}
