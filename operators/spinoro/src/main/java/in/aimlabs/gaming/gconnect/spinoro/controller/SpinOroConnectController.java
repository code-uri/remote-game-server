package in.aimlabs.gaming.gconnect.spinoro.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.dto.GameReplayRequest;
import in.aimlabs.gaming.services.IGameLaunchService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.spinoro.service.SpinOroPlayerServiceAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Base64;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/spinoro")
public class SpinOroConnectController {

    @Value("${rgs.player.connector.spinoro.partner:spinoro}")
    String partner;

    @Autowired
    IGameLaunchService gameLaunchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    SpinOroPlayerServiceAdaptor spinOroPlayerServiceAdaptor;

    @GetMapping(value = { "/launchGame" })
    public Mono<ResponseEntity<Object>> gameLaunch(
            ServerWebExchange exchange) {

        String query = new String(Base64.getDecoder()
                .decode(exchange.getRequest().getQueryParams().keySet().stream().findFirst().orElse("")));

        MultiValueMap<String, String> queryParams = UriComponentsBuilder
                .fromUri(URI.create("http://localhost?" + query)).build().getQueryParams();

        log.info("{}", queryParams);
        ServerHttpRequest request = exchange.getRequest();
        GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();

        gameLaunchRequest.setToken(queryParams.getFirst("securityToken"));
        gameLaunchRequest.setExternalToken(queryParams.getFirst("securityToken"));
        gameLaunchRequest.setTenant(queryParams.getFirst("tenant"));
        gameLaunchRequest.setBrand(queryParams.getFirst("brandId"));
        gameLaunchRequest.setNetwork(partner);
        gameLaunchRequest.setGameId(queryParams.getFirst("providerGameId"));
        gameLaunchRequest.setLanguage(queryParams.getFirst("language"));
        gameLaunchRequest.setLocale(queryParams.getFirst("language"));
        gameLaunchRequest.setDemo(!"1".equals(queryParams.getFirst("playMode")));
        gameLaunchRequest
                .setGamePlayMode("1".equals(queryParams.getFirst("playMode")) ? GamePlayMode.REAL : GamePlayMode.DEMO);
        gameLaunchRequest.setPlayer(queryParams.getFirst("playerId"));
        gameLaunchRequest.setCurrency(queryParams.getFirst("currency"));
        gameLaunchRequest.setLobbyUrl(queryParams.getFirst("lobbyURL"));

        gameLaunchRequest.setJurisdiction(queryParams.getFirst("jurisdiction"));

        if (gameLaunchRequest.getJurisdiction() != null) {
            if (queryParams.getFirst("realityCheckInterval") != null)
                gameLaunchRequest.setRealityCheckIntervalInMilliSeconds(
                        Integer.parseInt(Objects.requireNonNull(queryParams.getFirst("realityCheckInterval"))) * 1000L);

            if (gameLaunchRequest.getRealityCheckIntervalInMilliSeconds() > 0
                    && queryParams.getFirst("realityCheckStartTime") != null)
                gameLaunchRequest.setElapsedTimeInMilliSeconds(
                        Integer.parseInt(Objects.requireNonNull(queryParams.getFirst("realityCheckStartTime")))
                                * 1000L);
        }

        if (queryParams.getFirst("audio") != null)
            gameLaunchRequest.setAudio(Integer.parseInt(Objects.requireNonNull(queryParams.getFirst("audio"))));

        return gameLaunchService
                .launchGame(gameLaunchRequest)
                .map(uri -> {
                    String gameUrlPath = uri.toString();
                    return URI.create(gameUrlPath);
                })
                .tap(() -> (TapOnNextSignalListener<URI>) location -> {
                    log.info("SpinOro game url {}", location);
                })

                .map(location -> {
                    return ResponseEntity
                            .status(HttpStatus.FOUND)
                            .location(location)
                            .build();
                    // ResponseEntity.ok().location(location).build()
                })
                .doOnError(throwable -> {
                    log.error("SpinOro Game launch failed!", throwable);
                })
                .checkpoint("SpinOro game launch request.", true);
    }

    @GetMapping(value = { "/history" })
    public Mono<ResponseEntity<Object>> gameRoundDetails(ServerWebExchange exchange, @RequestParam String roundId,
            @RequestParam(required = false) String playerId) {

        GameReplayRequest replayRequest = new GameReplayRequest();
        replayRequest.setPlayerId(playerId);
        replayRequest.setGameRound(roundId);

        return gameLaunchService.gameReplay(replayRequest)
                .map(location -> {
                    return ResponseEntity
                            .status(HttpStatus.FOUND)
                            .location(location)
                            .build();
                    // ResponseEntity.ok().location(location).build()
                });
    }

}
