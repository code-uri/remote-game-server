package aimlabs.gaming.rgs.gameskins;


import java.net.URI;
import aimlabs.gaming.rgs.gameoperators.GameReplayRequest;

/**
 * Blocking game launch abstraction used by bf-games connect endpoints.
 *
 * Implemented by the main server module (which depends on this operator module).
 */
public interface IGameLaunchService {

    URI launchGame(GameLaunchRequest gameLaunchRequest);
    URI gameReplay(GameReplayRequest gameReplayRequest);
}
