package in.aimlabs.gaming.gconnect.reevo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.services.IGameLaunchService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.reevo.service.ReevoPlayerServiceAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/reevo")
public class ReevoConnectController {

    @Value("${rgs.player.connector.reevo.partner:reevo}")
    String partner;

    @Autowired
    IGameLaunchService gameLaunchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    ReevoPlayerServiceAdaptor reevoPlayerServiceAdaptor;

    @Getter
    @Setter
    @ToString
    static class GetGameRequest {

        String gameid;
        String play_for_fun = "0";

        // string (URL for exit to casino)
        String homeurl;
        String cashierurl;
        String lang;

        // (session id that will be send)
        String user_password;
        // string (id of player)
        String user_id;
        String user_nickname;
        // string (currency name)
        String currency;
        String operator;
        String channel;
        String country;
        String player_ip;
    }

    @Getter
    @Setter
    @ToString
    static class GetGameResponse {
        int error = 1;
        String response;
        String gamesession_id;
        String sessionid;
        String currency;
        String message;

    }

    @PostMapping(value = { "/getGame" })
    public Mono<GetGameResponse> launch(
            @RequestBody GetGameRequest launchRequest,
            ServerWebExchange exchange, @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        ServerHttpRequest request = exchange.getRequest();
        boolean isDemo = launchRequest.getPlay_for_fun().equals("1");

        GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
        // casino_id + user_id + currency + game
        // String token = launchRequest.getCasino_id() + "-" +
        // launchRequest.getUser().id + "-" + launchRequest.getCurrency() + "-" +
        // launchRequest.getGame();
        gameLaunchRequest.setToken(isDemo ? "DEMO-" + UUID.randomUUID() : null);
        gameLaunchRequest.setBrand(launchRequest.getOperator());
        gameLaunchRequest.setNetwork(partner);
        gameLaunchRequest.setGameId(launchRequest.getGameid());
        gameLaunchRequest.setLanguage(launchRequest.getLang());
        gameLaunchRequest.setLocale(launchRequest.getLang());
        gameLaunchRequest.setDemo(isDemo);
        gameLaunchRequest.setGamePlayMode(isDemo ? GamePlayMode.DEMO : GamePlayMode.REAL);
        gameLaunchRequest.setLobbyUrl(launchRequest.getHomeurl());
        gameLaunchRequest.setDepositUrl(launchRequest.getCashierurl());
        gameLaunchRequest.setPlayer(launchRequest.getUser_id());
        gameLaunchRequest.setCurrency(launchRequest.getCurrency());
        gameLaunchRequest.setIpAddress(launchRequest.getPlayer_ip());

        if (!isDemo) {
            gameLaunchRequest.setInitSession(true);
        }

        return gameLaunchService
                .launchGame(gameLaunchRequest)
                .map(uri -> {
                    String gameUrlPath = uri.toString();

                    return URI.create(gameUrlPath);
                })
                .tap(() -> (TapOnNextSignalListener<URI>) location -> {
                    log.info("Reevo game url {}", location);
                })
                .map(location -> {
                    MultiValueMap<String, String> queryParams = UriComponentsBuilder
                            .fromUriString(location.toString()).build().getQueryParams();
                    GetGameResponse response = new GetGameResponse();
                    response.setResponse(location.toString());

                    response.setGamesession_id(gameLaunchRequest.getToken());
                    response.setSessionid(response.getGamesession_id());
                    response.setCurrency(launchRequest.getCurrency());
                    // response.setSessionid(queryParams.getFirst("token"));
                    response.setError(0);
                    // log.info("{}", response);

                    return response;
                })
                .tap(() -> new DefaultSignalListener<GetGameResponse>() {
                    @Override
                    public void doOnNext(GetGameResponse response) {
                        log.info("{}", response);
                    }
                })
                .onErrorResume(throwable -> {
                    GetGameResponse response = new GetGameResponse();
                    response.setMessage("Game launch failed!");
                    response.setError(1);
                    log.error("Reevo Game launch failed!", throwable);
                    return Mono.just(response);
                })
                .checkpoint("Reevo game launch request.", true);

    }

    @Data
    public static class GetGameListRequest {
        String api_login;
        String api_password;
        String getGameList;
        String currency;
    }

    @Data
    public static class GetGameListResponse {
        int error;
        List<GameData> response;
    }

    @Data
    public static class GameData {
        String id;
        String name;
        String type;
        String subcategory;
        // String category;
        String licence;
        String gamename;
        String freerounds_supported;

        boolean play_for_fun_supported = true;
        String rtp;

        SlotGameDetails details;
    }

    @Data
    public static class SlotGameDetails {
        String reels;
        String lines;
        String freespins = "yes";
        String bonusgame = "yes";
    }

    @PostMapping(value = { "/getGameList" })
    public Mono<GetGameListResponse> getGames(
            @RequestBody GetGameListRequest request,
            ServerWebExchange exchange) {

        return reevoPlayerServiceAdaptor.getGames(request)
                .checkpoint("Reevo get games list request.", true);

    }
}
