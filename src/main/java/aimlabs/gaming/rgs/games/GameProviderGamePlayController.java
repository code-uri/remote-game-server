package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.brandgames.BrandGameService;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.dto.SearchRequest;
import aimlabs.gaming.rgs.core.dto.SearchResponse;
import aimlabs.gaming.rgs.core.dto.SortOrder;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.GameSessionContext;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.GameSkinService;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.settings.GameSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/games")
@Slf4j
@Data
public class GameProviderGamePlayController {

    @Autowired
    GameSessionBearerTokenProvider tokenProvider;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IPlayerService playerService;

    @Autowired
    GameRoundService gameRoundService;

    @Autowired
    MongoTemplate mongoTemplate;

    @Value("${app.player.auth-header:Authorization}")
    private String authHeader;


    @Value("${app.player.log-demo-requests:false}")
    private String logDemoRequests;

    @Autowired
    private IGameSessionService gameSessionService;

    @Autowired
    private IBrandService brandService;

    @Autowired
    private GameSkinService gameSkinService;

    @Autowired
    private GameRequestHandler gameRequestHandler;

    @Autowired
    private GameSettingsService gameSettingsService;

    @Autowired
    private BrandGameService brandGameService;


    @PostMapping("/play")
    public JsonNode playGame(
            GameSession gameSession,
            @RequestBody JsonNode request,
            HttpServletRequest httpServletRequest) {

       // java
       return ScopedValue.where(
               GameSessionContext.GAME_SESSION, gameSession
       ).call(() -> gameRequestHandler.playGame(gameSession, request));
    }

    @GetMapping("/game-rounds/{uid}/confirm")
    public void confirmHand(GameSession gameSession,
                            @PathVariable(name = "uid") String uid,
                            HttpServletRequest httpServletRequest) {

        GameRound gameRound = ScopedValue.where(
                GameSessionContext.GAME_SESSION, gameSession
        ).call(() -> gameRoundService.confirmHand(uid));
        if (gameRound == null || !gameRound.isHandConfirmed())
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, "Request failed!");
    }


    @PostMapping("/game-rounds/{uid}/ack")
    public void ack(GameSession gameSession,
                    @RequestBody JsonNode request,
                    @PathVariable(name = "uid") String uid,
                    HttpServletRequest httpServletRequest) {
        //String ipaddress = getRemoteIPAddress(serverHttpRequest);
        long startMillis = System.currentTimeMillis();
        log.info("Received ack request for game-round: {} ", uid);
        JsonNode response = ScopedValue.where(
                GameSessionContext.GAME_SESSION, gameSession
        ).call(() -> gameRequestHandler
                .ack(gameSession, uid, request));

    }

    @GetMapping("/settings")
    Map<String, Object> settings(GameSession gameSession, @RequestParam String brand, @RequestParam String game) {

        return ScopedValue.where(
                GameSessionContext.GAME_SESSION, gameSession
        ).call(() -> gameSettingsService.getBrandGameSettings(gameSession.getTenant(), brand, game));
    }


    @PostMapping("/initialise")
    ResponseEntity<JsonNode> initialiseGame(@RequestBody() GameInitialiseRequest req,
                                            HttpServletRequest httpServletRequest) {
        Pair<GameSession, JsonNode> pair = gameRequestHandler
                .initialiseGame(req.getToken(), req.getBrand(), req.getGameId());

        String jwtToken = tokenProvider.createToken(pair.getFirst(),
                req.getGameId());
        return ResponseEntity.ok()
                .header(authHeader, "Bearer " + jwtToken)
                .body(pair.getSecond());
    }


    @GetMapping("/history")
    SearchResponse<JsonNode>
    gameHistory(GameSession gameSession,
                @RequestParam(required = false, name = "gameId") String gameId,
                @RequestParam(required = true, name = "page", defaultValue = "0") String pageStr,
                @RequestParam(required = true, name = "size", defaultValue = "10") String sizeStr,
                HttpServletRequest httpServletRequest) {
        long startTime = System.currentTimeMillis();

        // 'player' : ?0 ,'gameId':  ?1,  'status' :  'COMPLETED'
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.getFilters().put("status", List.of("COMPLETED"));
        searchRequest.setPage(Integer.parseInt(pageStr));
        searchRequest.setSize(Integer.parseInt(sizeStr));
        searchRequest.getProperties().put("player", gameSession.getPlayer());
        SortOrder sortOrder = new SortOrder("DESC", "id");
        LinkedList<SortOrder> sortList = new LinkedList<>();
        sortList.add(sortOrder);
        searchRequest.setSort(sortList);
        if (StringUtils.hasText(gameId))
            searchRequest.getProperties().put("gameId", gameId);

        return ScopedValue.where(
                GameSessionContext.GAME_SESSION, gameSession
        ).call(() -> gameRequestHandler.history(searchRequest));
    }

    @GetMapping("/game-rounds/{uid}/details")
    ResponseEntity<JsonNode>
    gameRoundDetails(@PathVariable String uid,
                     @RequestHeader(value = "tenant", defaultValue = "default") String tenant) {

        GameRound gameRound = gameRoundService.getStore().getGameRoundDetails(uid);

        if (gameRound == null)
            throw new BaseRuntimeException(SystemErrorCode.NOT_FOUND);

        JsonNode response = gameRequestHandler
                .gamePlayAndActivityDetails(gameRound);

        return ResponseEntity.ok(response);
    }

    private String getRemoteIPAddress(HttpServletRequest request) {
        String remoteAddress = request.getHeader("X-Forwarded-For");
        if (remoteAddress == null) {
            return request.getRemoteAddr();
        }
        if (remoteAddress.contains(",")) {
            return remoteAddress.split(",")[0];
        }
        return remoteAddress;
    }
}
