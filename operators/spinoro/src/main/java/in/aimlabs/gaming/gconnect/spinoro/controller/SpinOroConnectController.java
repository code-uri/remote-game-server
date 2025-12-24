package in.aimlabs.gaming.gconnect.spinoro.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GamePlayMode;
import aimlabs.gaming.rgs.gameoperators.GameReplayRequest;
import aimlabs.gaming.rgs.gameskins.IGameLaunchService;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.spinoro.service.SpinOroPlayerServiceAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

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
    SpinOroPlayerServiceAdaptor spinOroPlayerServiceAdaptor;

    @GetMapping(value = { "/launchGame" })
    public ResponseEntity<Object> gameLaunch(HttpServletRequest request) {

        String query = new String(Base64.getDecoder()
                .decode(request.getParameterMap().keySet().stream().findFirst().orElse("")));

        MultiValueMap<String, String> queryParams = UriComponentsBuilder
                .fromUri(URI.create("http://localhost?" + query)).build().getQueryParams();

        log.info("{}", queryParams);
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

        try {
            URI location = gameLaunchService.launchGame(gameLaunchRequest);
            log.info("SpinOro game url {}", location);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        } catch (Exception e) {
            log.error("SpinOro Game launch failed!", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = { "/history" })
    public ResponseEntity<Object> gameRoundDetails(@RequestParam String roundId,
            @RequestParam(required = false) String playerId) {

        GameReplayRequest replayRequest = new GameReplayRequest();
        replayRequest.setPlayerId(playerId);
        replayRequest.setGameRound(roundId);

        try {
            URI location = gameLaunchService.gameReplay(replayRequest);
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        } catch (Exception e) {
            log.error("SpinOro replay failed!", e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
