package aimlabs.gaming.rgs.games;

import com.fasterxml.jackson.databind.JsonNode;

public interface GameFlowPipelineHandler {
    void handle(JsonNode request, GamePlayContext context);
    void setNext(GameFlowPipelineHandler nextHandler);
}
