package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.streaks.StreakPromotionGameFlowPipelineHandler;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class GameFlowPipeline {

    GameFlowPipelineHandler first;

    GameFlowPipeline(GamePlayGameFlowPipelineHandler gamePlayHandler,
            WagerGameFlowPipelineHandler wagerHandler,
            WinGameFlowPipelineHandler winHandler,
            StreakPromotionGameFlowPipelineHandler streakPromotionHandler) {
        this.first = gamePlayHandler;
        gamePlayHandler.setNext(streakPromotionHandler);
        streakPromotionHandler.setNext(wagerHandler);
        wagerHandler.setNext(winHandler);
    }

    public void handle(JsonNode request, GamePlayContext context) {
        getFirst().handle(request, context);
    }

}
