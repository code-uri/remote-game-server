package aimlabs.gaming.rgs.gamesessions;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.GameSessionAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.security.Principal;

@Slf4j
@Component
public class GameSessionArgumentResolver implements HandlerMethodArgumentResolver {

    @Autowired
    private IGameSessionService gameSessionService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == GameSession.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  @Nullable ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  @Nullable WebDataBinderFactory binderFactory) {

        Principal principal = webRequest.getUserPrincipal();
        if (!(principal instanceof GameSessionAuthenticationToken token)) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
        }

        String sessionUid = (String) token.getPrincipal();
        GameSession gameSession = gameSessionService.findOneByUid(sessionUid);
        if (gameSession == null) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
        }

        gameSessionService.keepSessionAlive(gameSession);
        return gameSession;
    }
}