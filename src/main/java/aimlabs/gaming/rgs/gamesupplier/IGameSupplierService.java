package aimlabs.gaming.rgs.gamesupplier;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.games.GameLaunchRequest;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;

import java.net.URI;

public interface IGameSupplierService {

    URI launchGame(GameLaunchRequest gameLaunchRequest);

    default URI replayGameRound(GameSession gameSession, GameRound gameRound, GameSkin gameSkin, Brand brand){
        throw new UnsupportedOperationException();
    }
}
