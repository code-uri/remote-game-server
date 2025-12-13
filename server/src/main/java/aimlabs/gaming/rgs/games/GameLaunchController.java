package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.GameReplayRequest;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Slf4j
@RestController
@RequestMapping("/games")
public class GameLaunchController {

    @Autowired
    GameRequestHandler gameRequestHandler;

    @Autowired
    IBrandGameService brandGameService;

    @Autowired
    GameSessionBearerTokenProvider tokenProvider;

    @GetMapping(value = "/launch/{token}")
    public ResponseEntity<?> launchGame(@PathVariable(value = "token", required = true) String token,
            @RequestParam(value = "gameId", required = true) String gameId,
            @RequestParam(value = "brand", required = true) String brand,
            HttpServletRequest request, @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        Map<String, String> queryParams = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0)
                queryParams.put(key, values[0]);
        });
        queryParams.put("token", token);

        String partner = brand;

        GameLaunchRequest launchRequest = new GameLaunchRequest(null, partner, brand, token, gameId, queryParams);
        launchRequest.setIpAddress(getRemoteIPAddress(request));
        launchRequest.setBrandUrl(getRefererHeader(request).orElse(null));

        URI uri = gameRequestHandler
                .launchGame(launchRequest);

        if (uri == null) {
            throw new BaseRuntimeException(SystemErrorCode.GAME_COMING_SOON);
        }

        if (LaunchMode.REDIRECT == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(uri)
                    .build();
        } else if (LaunchMode.EMBEDDED == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .location(uri)
                    .build();
        } else if (LaunchMode.JSON == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(uri.toString());
        } else {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(uri)
                    .build();
        }
    }

    @GetMapping(value = "/replay/round")
    public ResponseEntity<URI> replay(@RequestParam String roundId,

            // operator integration params
            @RequestParam(name = "clientId", required = false) String clientId,
            @RequestParam(name = "signature", required = false) String signature,

            // player game round request
            @RequestHeader(name = "Authorization", required = false) String token) {

        GameReplayRequest gameReplayRequest = new GameReplayRequest();
        gameReplayRequest.setGameRound(roundId);

        if (token != null) {
            Claims claims = tokenProvider.validateToken(token);
            if (claims.get("roles") != null) {
                // admin user with roles validate role permission for accessing the game round.
            }

            if (claims.get("player") != null) {
                String player = claims.get("player", String.class);
                // String gameId = claims.get("game", String.class);
                gameReplayRequest.setPlayerId(player);
            }
        }

        URI uri = gameRequestHandler
                .gameReplay(gameReplayRequest);

        if (uri == null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME_ROUND);

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(uri)
                .<URI>build();
    }

    @GetMapping("/replay/initialise")
    ResponseEntity<JsonNode> initialiseGame(@RequestParam String roundId, HttpServletRequest httpServletRequest) {

        JsonNode response = gameRequestHandler
                .replayGameRoundInitialiseGame(roundId);
        return ResponseEntity.ok()
                .body(response);
    }

    private String getRemoteIPAddress(HttpServletRequest request) {
        String remoteAddress = request.getHeader("X-Forwarded-For");
        if (remoteAddress == null)
            return request.getRemoteAddr();
        if (remoteAddress.contains(","))
            return remoteAddress.split(",")[0];

        return remoteAddress;
    }

    private Optional<String> getRefererHeader(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader("Referer"));
    }

}
