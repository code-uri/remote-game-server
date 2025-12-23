package aimlabs.gaming.rgs.gameskins;


import java.net.URI;

/**
 * Blocking game launch abstraction used by bf-games connect endpoints.
 *
 * Implemented by the main server module (which depends on this operator module).
 */
public interface IGameLaunchService {

    URI launchGame(GameLaunchRequest gameLaunchRequest);
}
