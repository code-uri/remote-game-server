package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.networks.Network;
import aimlabs.gaming.rgs.players.Player;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class GamePlayContext {
    JsonNode gamePlayRequest;
    GamePlayResponse engineResponse;
    Map<String, Object> settings;
    Brand brand;
    GameSkin gameSkin;
    Network network;
    Connector connector;
    GameSession gameSession;
    Player player;

    GamePlayContext(GameSession gameSession, Brand brand, GameSkin gameSkin){
        this.gameSession = gameSession;
        this.brand = brand;
        this.gameSkin = gameSkin;
    }
}
