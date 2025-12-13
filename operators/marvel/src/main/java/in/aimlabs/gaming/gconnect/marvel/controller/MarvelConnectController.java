package in.aimlabs.gaming.gconnect.marvel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.GamePlayMode;
import in.aimlabs.gaming.services.IGameLaunchService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import aimlabs.gaming.rgs.core.utils.UUIDShortener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import in.aimlabs.gaming.gconnect.marvel.service.MarvelPlayerServiceAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.observability.DefaultSignalListener;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/marvel")
public class MarvelConnectController {

    @Value("${rgs.player.connector.marvel.partner:marvel}")
    String partner;

    @Autowired
    IGameLaunchService gameLaunchService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    MarvelPlayerServiceAdaptor marvelPlayerServiceAdaptor;

    static record GameLogin(String type, String tableId, String userName, String currency, String lang,
            String reloadUrl) {
    }

    static record GameLoginResponse(String type, String url, String status, String token) {
    }

    static record GetBalance(String type, String token) {
    }

    static record GetBalanceResponse(String type, String username, String status, String currency, double balance) {
    }

    static record FundTransfer(String type, String transactionId, String token, String round, String tableId,
            String currency, double amount, String userName) {
    }

    static record FundTransferResponse(String type, String userName, String status, String currency,
            double balance, String token, String transactionId) {
    }

    static record GameSettlement(String type, String userName, String transactionId, String token, String round,
            String tableId, String currency, double amount) {

    }

    static record GameSettlementResponse(String type, String user, String status, String token, String currency,
            double balance) {

    }

    @PostMapping(value = { "/gameLogin" })
    public Mono<GameLoginResponse> gameLogin(
            @RequestBody GameLogin loginRequest,
            ServerWebExchange exchange, @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        ServerHttpRequest request = exchange.getRequest();

        GameLaunchRequest gameLaunchRequest = new GameLaunchRequest();
        gameLaunchRequest.setToken(UUIDShortener.shortenUUID());

        gameLaunchRequest.setBrand(partner);
        gameLaunchRequest.setNetwork(partner);
        gameLaunchRequest.setGameId(loginRequest.tableId);
        gameLaunchRequest.setLanguage(loginRequest.lang);
        gameLaunchRequest.setLocale(loginRequest.lang);
        gameLaunchRequest.setDemo(false);
        gameLaunchRequest.setGamePlayMode(GamePlayMode.REAL);
        gameLaunchRequest.setPlayer(loginRequest.userName);
        gameLaunchRequest.setCurrency(loginRequest.currency);

        return gameLaunchService
                .launchGame(gameLaunchRequest)
                .map(uri -> {
                    String gameUrlPath = uri.toString();

                    return URI.create(gameUrlPath);
                })
                .tap(() -> (TapOnNextSignalListener<URI>) location -> {
                    log.info("Marvel game url {}", location);
                })

                .map(location -> {
                    return new GameLoginResponse("login", location.toString(), "success", gameLaunchRequest.getToken());
                })
                .tap(() -> new DefaultSignalListener<GameLoginResponse>() {
                    @Override
                    public void doOnNext(GameLoginResponse response) {
                        log.info("{}", response);
                    }
                })
                .onErrorResume(throwable -> {
                    GameLoginResponse response = new GameLoginResponse("login", null, "failure", null);
                    log.error("Marvel Game launch failed!", throwable);
                    return Mono.just(response);
                })
                .checkpoint("Marvel game launch request.", true);
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

    /*
     * @PostMapping(value = {"/getGameList"})
     * public Mono<GetGameListResponse> getGames(
     * 
     * @RequestBody GetGameListRequest request,
     * ServerWebExchange exchange
     * ) {
     * 
     * return marvelPlayerServiceAdaptor.getGames(request)
     * .checkpoint("Marvel get games list request.", true);
     * 
     * }
     */
}
