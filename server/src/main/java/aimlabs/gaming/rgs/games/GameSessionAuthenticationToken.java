package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.gamesessions.GameSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

public class GameSessionAuthenticationToken extends UsernamePasswordAuthenticationToken {


    public GameSessionAuthenticationToken(String token, String jwt) {
        super(token, jwt);
    }

    public String getName() {
        if(getPrincipal() instanceof GameSession gameSession){
            return gameSession.getPlayer();
        }
        return "";
    }
}
