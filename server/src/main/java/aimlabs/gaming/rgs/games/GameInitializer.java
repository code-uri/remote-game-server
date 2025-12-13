package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface GameInitializer {

    JsonNode loadData(GameSession gameSession, GameSkin gameSkin, Map<String,Object> settings);
}
