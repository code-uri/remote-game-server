package aimlabs.gaming.rgs.aggregation;

import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.gameoperators.*;
import aimlabs.gaming.rgs.gamerounds.IGameRoundService;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.transactions.ITransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/connect")
@Data
@Slf4j
public class PlayerConnectController {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    IGameSessionService gameSessionService;

    @Autowired
    IPlayerService playerService;

    @Autowired
    IBrandService brandService;

    @Autowired
    IGameSkinService gameSkinService;

    @Autowired
    ITransactionService transactionService;

    @Autowired
    IGameRoundService gameRoundService;

    @Autowired
    AggregatorPlayerServiceManager aggregatorPlayerServiceManager;



    @PostMapping(
            value = "/player-initialise"
    )
    public PlayerInitialiseResponse playerInitialise(@RequestBody  PlayerInitialiseRequest request,
                                                     HttpServletRequest httpServletRequest,
                                                     @RequestHeader(defaultValue = "default") String tenant) {

        request.setTenant(tenant);
        log.info("Player Initialise Request: {}", request);
        return aggregatorPlayerServiceManager.playerInitialise(request);
    }

    @PostMapping(
            value = "/player-transaction"
    )
    public PlayerTransactionResponse playerTransaction(@RequestBody PlayerTransactionRequest request,
                                                       HttpServletRequest httpServletRequest,
                                                       @RequestHeader(defaultValue = "default") String tenant) {

        request.setTenant(tenant);
        log.info("Player transaction Request: {}", request);
        return aggregatorPlayerServiceManager.playerTransaction(request);
    }


    @PostMapping(
            value = "/player-balance"
    )
    public Wallet playerBalance(@RequestBody PlayerBalanceRequest request,
                                HttpServletRequest httpServletRequest,
                                @RequestHeader(defaultValue = "default") String tenant) {

        request.setTenant(tenant);
        log.info("Player balance Request: {}", request);
        return aggregatorPlayerServiceManager.playerBalance(request);

    }

    public PlayerTransactionResponse rollback(@RequestBody
                                              PlayerTransactionRequest request,
                                              HttpServletRequest httpServletRequest,
                                              @RequestHeader(defaultValue = "default") String tenant) {

        request.setTenant(tenant);
        log.info("Player rollback Request: {}", request);
        return aggregatorPlayerServiceManager.playerTransaction(request);
    }
}
