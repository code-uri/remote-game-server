package aimlabs.gaming.rgs.gamesessions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

@Slf4j
public class GameSessionAuthenticationManager implements AuthenticationManager {

    @Autowired
    IGameSessionService gameSessionService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if(authentication.getPrincipal() instanceof GameSession) {
            GameSession session = (GameSession) authentication.getPrincipal();
            gameSessionService.keepSessionAlive(session);
        }
        return authentication;
    }
}
