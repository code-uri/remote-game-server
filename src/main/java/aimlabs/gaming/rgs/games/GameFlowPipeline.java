package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.streaks.StreakPromotionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class GameFlowPipeline implements GameHandler {

    GameHandler first;

    GameFlowPipeline(GamePlayHandler gamePlayHandler,
                     WagerHandler wagerHandler,
                     WinHandler winHandler,
                     StreakPromotionHandler streakPromotionHandler) {
        this.first = gamePlayHandler;
        gamePlayHandler.setNext(streakPromotionHandler);
        streakPromotionHandler.setNext(wagerHandler);
        wagerHandler.setNext(winHandler);
    }

    @Override
    public void handle(JsonNode request, GamePlayContext context) {
        getFirst().handle(request, context);
    }

    @Override
    public void setNext(GameHandler nextHandler) {

    }
}
