package aimlabs.gaming.rgs.gamesessions;

import java.lang.ScopedValue;

public final class GameSessionContext {

    public static final ScopedValue<GameSession> GAME_SESSION = ScopedValue.newInstance();

    private GameSessionContext() {
    }

    public static GameSession current() {
        if (!GAME_SESSION.isBound()) {
            return null;
        }
        return GAME_SESSION.get();
    }
}