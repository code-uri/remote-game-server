package in.aimlabs.gaming.gconnect.slotegrator.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.services.IGameLaunchService;

import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.slotegrator.client.Signer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/slotegrator")
public class SlotegratorConnectController {

    @Value("${rgs.player.connector.slotegrator.partner:slotegrator}")
    String partner;

    @Autowired
    IGameLaunchService gameLaunchService;

    @Autowired
    private ObjectMapper objectMapper;

    // @Autowired
    // GameSkinService gameSkinService;

    @Getter
    @Setter
    @ToString
    static class GetGameUrlRequest {
        // string (id of client)
        String client_id;
        // string (id of game)
        String game_id;
        String language;
        // string (URL for exit to casino)
        String return_url;

        // (session id that will be send)
        String session_id;
        // string (id of player)
        String player_id;
        // string (currency name)
        String currency;
    }

    @Getter
    @Setter
    @ToString
    static class GetGameUrlResponse {
        String url;

        public GetGameUrlResponse(String url) {
            this.url = url;
        }
    }

    @PostMapping(value = { "/game-url" })
    public Mono<ResponseEntity<GetGameUrlResponse>> launch(
            @RequestBody GetGameUrlRequest launchRequest,
            ServerWebExchange exchange, @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        boolean isDemo = launchRequest.getSession_id() == null;

        GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
        // casino_id + user_id + currency + game
        // String token = launchRequest.getCasino_id() + "-" +
        // launchRequest.getUser().id + "-" + launchRequest.getCurrency() + "-" +
        // launchRequest.getGame();
        gameLaunchRequest.setToken(isDemo ? "DEMO-" + UUID.randomUUID() : launchRequest.getSession_id());
        gameLaunchRequest.setBrand(launchRequest.getClient_id().toLowerCase());
        gameLaunchRequest.setNetwork(partner);
        gameLaunchRequest.setGameId(launchRequest.getGame_id());
        gameLaunchRequest.setLanguage(launchRequest.getLanguage());
        gameLaunchRequest.setLocale(launchRequest.getLanguage());
        gameLaunchRequest.setDemo(isDemo);
        gameLaunchRequest.setGamePlayMode(isDemo ? GamePlayMode.DEMO : GamePlayMode.REAL);
        gameLaunchRequest.setLobbyUrl(launchRequest.getReturn_url());

        TypeReference<Map<String, String>> type = new TypeReference<>() {
        };
        Map<String, String> params = objectMapper.convertValue(gameLaunchRequest, type);
        Map<String, String> map = new HashMap<>(params);
        gameLaunchRequest.setParams(map);

        if (!isDemo) {
            gameLaunchRequest.setInitSession(true);
            gameLaunchRequest.getParams().put("playerId", launchRequest.getPlayer_id());
            gameLaunchRequest.getParams().put("currency", launchRequest.getCurrency());
            gameLaunchRequest.setPlayer(launchRequest.getPlayer_id());
            gameLaunchRequest.setCurrency(launchRequest.getCurrency());
        }

        String remoteHost = request.getHeaders().getFirst("X-Forwarded-Host");
        if (remoteHost == null)
            remoteHost = request.getRemoteAddress().getAddress().getHostName();
        if (remoteHost.contains(","))
            remoteHost = remoteHost.split(",")[0];

        String finalRemoteHost = remoteHost;

        return gameLaunchService
                .launchGame(gameLaunchRequest)
                .map(uri -> {
                    String gameUrlPath = uri.toString();

                    if (gameUrlPath.startsWith("/")) {
                        gameUrlPath = "https://" + finalRemoteHost + gameUrlPath;
                    } else if (!gameUrlPath.startsWith("http")) {
                        gameUrlPath = "https://" + finalRemoteHost + "/" + gameUrlPath;
                    }

                    return URI.create(gameUrlPath);
                })
                .tap(() -> (TapOnNextSignalListener<URI>) location -> {
                    log.info("Slotegrator game url {}", location);
                })
                .map(location -> {
                    GetGameUrlResponse gameLaunchResponse = new GetGameUrlResponse(location.toString());
                    // try {
                    // softSwissSigner.injectHeader(request, response,
                    // objectMapper.writeValueAsBytes(gameLaunchResponse));
                    // } catch (JsonProcessingException e) {
                    // return ResponseEntity
                    // .status(HttpStatus.INTERNAL_SERVER_ERROR).body(new GetGameUrlResponse(null));
                    // }
                    return ResponseEntity
                            .status(HttpStatus.OK)
                            .body(gameLaunchResponse);
                })
                .checkpoint("Slotegrator game launch request.", true);

    }
}
