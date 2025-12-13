package in.aimlabs.gaming.gconnect.bfgames.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.services.IGameLaunchService;
import in.aimlabs.money.currency.service.CurrencyService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import in.aimlabs.gaming.gconnect.bfgames.service.BFGamesPlayerServiceAdaptor;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;

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
    CurrencyService currencyService;

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

    @GetMapping(value = { "/games" })
    public Mono<List<GameData>> getGames(
            @RequestParam String currency,
            ServerWebExchange exchange) {

        return gamesPlayerServiceAdaptor.getGames(currency)

                .checkpoint("BFgames get games request.", true);

    }

    @GetMapping(value = { "/currencies" })
    public Mono<List<CurrencyInfo>> getCurrencies(
            ServerWebExchange exchange) {

        log.info("Headers {}", exchange.getRequest().getHeaders());
        return currencyService.findAllISOCurrencies()
                .filter(currencyDocument -> !"crypto".equals(currencyDocument.getType()))
                .map(currencyDocument -> new CurrencyInfo(currencyDocument.getCode())).collectList()
                .checkpoint("BFgames get currencies request.", true);

    }

    @PostMapping(value = { "/sessions" })
    public Mono<GameLaunchResponse> launch(
            @RequestBody LaunchRequest launchRequest,
            ServerWebExchange exchange,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        ServerHttpRequest request = exchange.getRequest();
        boolean isDemo = "DEMO".equals(launchRequest.getGameMode());
        return Mono.just(request)
                .tap(() -> new TapOnNextSignalListener<ServerHttpRequest>() {
                    public void doOnNext(ServerHttpRequest request) throws Throwable {
                        log.info("launch request path {} \t\t {} \t\t  headers {} ",
                                request.getPath(), launchRequest, request.getHeaders());
                    }
                })
                .flatMap(unused -> {
                    GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
                    // casino_id + user_id + currency + game
                    // String token = launchRequest.getCasino_id() + "-" +
                    // launchRequest.getUser().id + "-" +
                    // launchRequest.getCurrency() + "-" + launchRequest.getGame();
                    gameLaunchRequest.setToken(isDemo ? "DEMO-" + UUID.randomUUID() : launchRequest.token);
                    // gameLaunchRequest.setPartner(partner);
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
                    // if (!isDemo) {
                    gameLaunchRequest.setInitSession(true);
                    gameLaunchRequest.setPlayer(launchRequest.getPlayerId());
                    gameLaunchRequest.setCurrency(launchRequest.getCurrency());
                    // }

                    /*
                     * String remoteHost = request.getHeaders().getFirst("X-Forwarded-Host");
                     * if (remoteHost == null)
                     * remoteHost = request.getRemoteAddress().getAddress().getHostName();
                     * if (remoteHost.contains(","))
                     * remoteHost = remoteHost.split(",")[0];
                     * String finalRemoteHost = remoteHost;
                     */

                    return gameLaunchService
                            .launchGame(gameLaunchRequest)
                            .map(uri -> {
                                String gameUrlPath = uri.toString();
                                return URI.create(gameUrlPath);
                            })
                            .tap(() -> (TapOnNextSignalListener<URI>) location -> {
                                log.info("BFgames game url {}", location);
                            })
                            .map(location -> {
                                MultiValueMap<String, String> queryParams = UriComponentsBuilder
                                        .fromUriString(location.toString()).build().getQueryParams();
                                GameLaunchResponse response = new GameLaunchResponse();
                                response.setGameUrl(location.toString());

                                response.setSessionId(queryParams.getFirst("token"));
                                response.setVersion(queryParams.getFirst("version"));
                                return response;
                            })
                            .tap(() -> new DefaultSignalListener<GameLaunchResponse>() {
                                @Override
                                public void doOnNext(GameLaunchResponse response) {
                                    log.info("{}", response);
                                }
                            });
                })

                .checkpoint("BFgames launch request.", true);

    }
}
