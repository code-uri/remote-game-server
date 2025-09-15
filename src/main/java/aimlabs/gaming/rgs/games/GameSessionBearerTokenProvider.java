package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.core.utils.JwtUtil;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

@Slf4j
@Data
@Component
public class GameSessionBearerTokenProvider implements AuthenticationConverter {
    private static final String BEARER = "Bearer ";
    private static final Predicate<String> matchBearerLength = authValue -> authValue.length() > BEARER.length();
    protected static final Function<String, String> isolateBearerValue = authValue -> authValue.substring(BEARER.length());
    private final JwtUtil jwtUtil;

    public GameSessionBearerTokenProvider(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }

    private SecretKey secretKey;


    public String createToken(GameSession session, String game) {

        if (session.getCurrency() == null)
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Currency cannot be null.");

        if (session.getUid() == null)
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "GameSession uid cannot be null.");

        return jwtUtil.generateJws(new HashMap<>(),
                Map.of(
                        "sub", session.getUid(),
                        "jti", UUID.randomUUID().toString(),
                        "roles", "PLAYER",
                        "brand", session.getBrand(),
                        "game", session.getGame(),
                        "currency", session.getCurrency(),
                        "player", session.getPlayer()
        ), Duration.ofDays(1));
    }

    public Authentication getAuthentication(String bearerToken) {
        if (!StringUtils.hasText(bearerToken)) {
            throw new BadCredentialsException("Invalid token");
        }

        Claims claims = validateToken(bearerToken);
        if(claims==null)
            throw new BadCredentialsException("Invalid token");

        String token = claims.getSubject();
        return new GameSessionAuthenticationToken(token, bearerToken);
    }

    public Claims validateToken(String brearToken) {
        return jwtUtil.decodeJWT(brearToken);
    }



    public String getTokenFromRequest(HttpServletRequest request) {
        String authorization = request
                .getHeader(HttpHeaders.AUTHORIZATION);
        log.info("Found Authorization header {}", authorization);

        return StringUtils.hasText(authorization) ? authorization : null;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        String token = isolateBearerValue.apply(getTokenFromRequest(request));

        return getAuthentication(token);
    }
}