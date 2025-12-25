package aimlabs.gaming.rgs.gconnect.reevo.controller;

import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.GamePlayMode;
import aimlabs.gaming.rgs.gameskins.IGameLaunchService;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import aimlabs.gaming.rgs.gconnect.reevo.service.ReevoPlayerServiceAdaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

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
    public ResponseEntity<GetGameResponse> launch(
            @RequestBody GetGameRequest launchRequest,
            HttpServletRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        boolean isDemo = "1".equals(launchRequest.getPlay_for_fun());

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

        try {
            URI location = gameLaunchService.launchGame(gameLaunchRequest);
            log.info("Reevo game url {}", location);

            GetGameResponse response = new GetGameResponse();
            response.setResponse(location != null ? location.toString() : null);
            response.setGamesession_id(gameLaunchRequest.getToken());
            response.setSessionid(response.getGamesession_id());
            response.setCurrency(launchRequest.getCurrency());
            response.setError(0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            GetGameResponse response = new GetGameResponse();
            response.setMessage("Game launch failed!");
            response.setError(1);
            log.error("Reevo Game launch failed!", e);
            return ResponseEntity.ok(response);
        }

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
    public ResponseEntity<GetGameListResponse> getGames(
            @RequestBody GetGameListRequest request,
            HttpServletRequest httpServletRequest) {

        try {
            GetGameListResponse response = reevoPlayerServiceAdaptor.getGames(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Reevo get games list request failed!", e);
            return ResponseEntity.internalServerError().build();
        }

    }
}
