package aimlabs.gaming.rgs.gamesupplier;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gamesessions.GameSession;

import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;

import java.net.URI;

public interface IGameSupplierService {

    URI launchGame(GameLaunchRequest gameLaunchRequest, String player, String currency, GameSkin gameSkin,
            String gameConfiguration, Brand brand);

    Connector getConnector();        

    default URI replayGameRound(GameSession gameSession, String gameRound, GameSkin gameSkin, Brand brand) {
        throw new UnsupportedOperationException();
    }

}
