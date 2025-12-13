package in.aimlabs.gaming.gconnect.softsiss.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.services.IGameLaunchService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import in.aimlabs.gaming.gconnect.softsiss.client.Signer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/softswiss")
public class SoftSwissConnectController {

    @Value("${rgs.player.connector.softswiss.partner:softswiss}")
    String partner;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IGameLaunchService gameLaunchService;

    @Getter
    @Setter
    @ToString
    static class Urls {
        String return_url;
        String deposit_url;
    }

    @Getter
    @Setter
    @ToString
    static class User {
        String id;
        String external_id;
        String email;
        String firstname;
        String lastname;
        String nickname;
        String city;
        String date_of_birth;
        String registered_at;
        String gender;
        String country;
    }

    @Getter
    @Setter
    @ToString
    static class LaunchRequest {
        String casino_id;
        String game;
        String currency;
        String locale;
        String ip;
        String client_type;
        String balance;
        Urls urls;
        User user;
        String jurisdiction;
        boolean demo;
    }

    @Getter
    @Setter
    @ToString
    static class GameLaunchResponse {
        LaunchOptions launch_options;

        @AllArgsConstructor
        static class LaunchOptions {
            String game_url;
            String strategy = "iframe";

            public String toString() {
                final StringBuilder sb = new StringBuilder("LaunchOptions{");
                sb.append("game_url='").append(game_url).append('\'');
                sb.append(", strategy='").append(strategy).append('\'');
                sb.append('}');
                return sb.toString();
            }
        }

        public String toString() {
            final StringBuilder sb = new StringBuilder("GameLaunchResponse{");
            sb.append("launch_options=").append(launch_options);
            sb.append('}');
            return sb.toString();
        }
    }

    @PostMapping(value = { "/sessions", "/demo" })
    public Mono<ResponseEntity<Map<String, Map<String, String>>>> launch(
            @RequestBody LaunchRequest launchRequest,
            ServerWebExchange exchange, @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        boolean isDemo = request.getPath().toString().contains("demo");
        launchRequest.setDemo(isDemo);
        return Mono.just(request)
                .tap(() -> new TapOnNextSignalListener<ServerHttpRequest>() {
                    public void doOnNext(ServerHttpRequest request) throws Throwable {
                        log.info("launch request path {} \t\t {} \t\t  headers {} ", request.getPath(), launchRequest,
                                request.getHeaders());
                    }
                })
                .flatMap(unused -> {
                    GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
                    // casino_id + user_id + currency + game
                    // String token = launchRequest.getCasino_id() + "-" +
                    // launchRequest.getUser().id + "-" + launchRequest.getCurrency() + "-" +
                    // launchRequest.getGame();
                    gameLaunchRequest.setToken(isDemo ? "DEMO-" + UUID.randomUUID() : null);
                    gameLaunchRequest.setIpAddress(launchRequest.getIp());
                    gameLaunchRequest.setNetwork(partner);
                    gameLaunchRequest.setBrand(launchRequest.getCasino_id());
                    gameLaunchRequest.setGameId(launchRequest.getGame());
                    gameLaunchRequest.setLanguage(launchRequest.getLocale());
                    gameLaunchRequest.setLocale(launchRequest.getLocale());
                    gameLaunchRequest.setDemo(isDemo);
                    gameLaunchRequest.setGamePlayMode(isDemo ? GamePlayMode.DEMO : GamePlayMode.REAL);
                    gameLaunchRequest.setDepositUrl(launchRequest.getUrls().getDeposit_url());
                    gameLaunchRequest.setLobbyUrl(launchRequest.getUrls().getReturn_url());

                    TypeReference<Map<String, String>> type = new TypeReference<>() {
                    };
                    Map<String, String> params = objectMapper.convertValue(gameLaunchRequest, type);
                    Map<String, String> map = new HashMap<>(params);
                    gameLaunchRequest.setParams(map);

                    if (!isDemo) {
                        gameLaunchRequest.setInitSession(true);
                    }
                    gameLaunchRequest.setPlayer(launchRequest.getUser().getId());
                    gameLaunchRequest.setCurrency(launchRequest.getCurrency());
                    gameLaunchRequest.getParams().put("playerId", launchRequest.getUser().getId());
                    gameLaunchRequest.getParams().put("currency", launchRequest.getCurrency());

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
                                log.info("Softswiss game url {}", location);
                            })
                            .map(location -> {
                                Map<String, String> options = new HashMap<>();
                                options.put("game_url", location.toString());
                                options.put("strategy", "iframe");
                                Map<String, Map<String, String>> rs = new HashMap<>();
                                rs.put("launch_options", options);

                                return ResponseEntity
                                        .status(HttpStatus.OK)
                                        .body(rs);
                            });
                    /*
                     * .tap(() -> (TapOnNextSignalListener<ResponseEntity<Object>>)
                     * gameLaunchResponse -> {
                     * log.info("{}", gameLaunchResponse);
                     * });
                     */
                })

                .checkpoint("SoftSwiss game launch request.", true);

    }

    private String getUrlFromQueryParams(Map<String, String> launchInfo, String urlQueryString) {
        return launchInfo.get("url") + "?" + urlQueryString;
    }

    private String getUrlQueryString(Map<String, String> launchInfo) {
        launchInfo.remove("tenant");
        launchInfo.remove("gameId");
        return launchInfo.entrySet()
                .stream()
                .filter(stringListEntry -> !stringListEntry.getKey().equals("externalToken")
                        && stringListEntry.getValue() != null && !stringListEntry.getKey().equals("url"))
                .map(entry -> UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8.toString()) + "=" +
                        UriUtils.encode(entry.getValue(), StandardCharsets.UTF_8.toString()))
                .collect(Collectors.joining("&"));
    }

    private String getTenant(ServerHttpRequest request) {
        String remoteHost = request.getHeaders().getFirst("X-Forwarded-Host");
        if (remoteHost == null)
            return request.getRemoteAddress().getAddress().getHostName();
        if (remoteHost.contains(","))
            return remoteHost.split(",")[0];

        if (remoteHost.contains(".mplaygames.com"))
            return "mplay";
        else
            return "default";
    }
}
