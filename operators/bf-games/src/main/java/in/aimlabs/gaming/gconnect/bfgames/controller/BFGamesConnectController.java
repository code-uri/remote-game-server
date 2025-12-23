package in.aimlabs.gaming.gconnect.bfgames.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GamePlayMode;
import aimlabs.gaming.rgs.gameskins.IGameLaunchService;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import in.aimlabs.gaming.gconnect.bfgames.service.BFGamesPlayerServiceAdaptor;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/bf-games/v1")
public class BFGamesConnectController {

    @Autowired
    IGameLaunchService gameLaunchService;

    @Autowired
    BFGamesPlayerServiceAdaptor gamesPlayerServiceAdaptor;

    @Autowired
    ICurrencyService currencyService;

    @GetMapping(value = { "/games" })
    public List<GameData> getGames(@RequestParam String currency) {
        return gamesPlayerServiceAdaptor.getGames(currency);
    }

    @GetMapping(value = { "/currencies" })
    public List<CurrencyInfo> getCurrencies() {
        return currencyService.findAllISOCurrencies().stream()
                .filter(c -> !"crypto".equals(c.getType()))
                .map(c -> new CurrencyInfo(c.getCode()))
                .toList();
    }

    @PostMapping(value = { "/sessions" })
    public GameLaunchResponse launch(
            @RequestBody LaunchRequest launchRequest,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        boolean isDemo = "DEMO".equals(launchRequest.getGameMode());
        log.info("launch request userAgent={} payload={}", userAgent, launchRequest);

        GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
        gameLaunchRequest.setToken(isDemo ? "DEMO-" + UUID.randomUUID() : launchRequest.token);
        gameLaunchRequest.setIpAddress(launchRequest.getIp());
        gameLaunchRequest.setBrand(launchRequest.getOperatorId() != null
                ? launchRequest.getOperatorId()
                : "bfgames");
        gameLaunchRequest.setGameId(launchRequest.getGameId());
        gameLaunchRequest.setLanguage(launchRequest.getLocale());
        gameLaunchRequest.setLocale(launchRequest.getLocale());
        gameLaunchRequest.setDemo(isDemo);
        gameLaunchRequest.setGamePlayMode(isDemo ? GamePlayMode.DEMO : GamePlayMode.REAL);
        gameLaunchRequest.setDepositUrl(launchRequest.getCashierUrl());
        gameLaunchRequest.setLobbyUrl(launchRequest.getLobbyUrl());
        gameLaunchRequest.getParams().put("playerId", launchRequest.getPlayerId());
        gameLaunchRequest.getParams().put("currency", launchRequest.getCurrency());
        gameLaunchRequest.setInitSession(true);
        gameLaunchRequest.setPlayer(launchRequest.getPlayerId());
        gameLaunchRequest.setCurrency(launchRequest.getCurrency());

        URI location = gameLaunchService.launchGame(gameLaunchRequest);
        log.info("BFgames game url {}", location);

        MultiValueMap<String, String> queryParams = UriComponentsBuilder
                .fromUriString(location.toString()).build().getQueryParams();
        GameLaunchResponse response = new GameLaunchResponse();
        response.setGameUrl(location.toString());
        response.setSessionId(queryParams.getFirst("token"));
        response.setVersion(queryParams.getFirst("version"));
        log.info("{}", response);
        return response;
    }

    @AllArgsConstructor
    @Data
    public static class CurrencyInfo {
        String iso;
    }

    @Getter
    @Setter
    @ToString
    public static class GameData {
        String id;
        String name;
        String version;
        @Nullable
        Integer lines;
        @Nullable
        int betRatio = 1;
        List<Integer> lineBetSteps;
        Integer minTotalBet;
        Integer maxTotalBet;
        Integer defaultLineBet;
        Integer defaultTotalBet;
        List<LicenseDetails> licenses;

        @Data
        @NoArgsConstructor
        public static class LicenseDetails {
            String license = "curacao";

            Integer[] rtps;

            @JsonProperty
            public void setRtps(Number[] rtps) {

                if (rtps != null) {
                    this.rtps = Arrays.stream(rtps).map(o -> Double.valueOf(o.doubleValue() * 100).intValue())
                            .toArray(Integer[]::new);
                }
            }

            public LicenseDetails(String license, Double[] rtps) {
                this.license = license;
                setRtps(rtps);
            }
        }

    }

    @Getter
    @Setter
    @ToString
    static class LaunchRequest {
        String gameId;
        String gameMode;

        String device;
        String currency;
        String playerId;
        String ip;
        String token;

        String locale;
        String name;
        String lobbyUrl;
        String cashierUrl;
        String historyUrl;
        String relaunchUrl;
        String cashierOpenMode;
        int demoBalance;
        String operatorId = "bfgames";
        String license;
        int rcInterval;
        int rcInitDelay;

        GameConfig gameConfig;

        static class GameConfig {
            Game game;
            Limits limits;
        }

        static class Game {
            int defaultLineBet;
            int[] lineBetSteps;

        }

        static class Limits {
            Bet bet;
            Win win;

            static class Bet {
                int maxBet;
                int minBet;
                int amount;

            }

            static class Win {

                int amount;
            }
        }

    }

    @Getter
    @Setter
    @ToString
    static class GameLaunchResponse {
        String gameUrl;
        String sessionId;
        String version;
    }
}
