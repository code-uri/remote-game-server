package aimlabs.gaming.rgs.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.games.GameRequestHandler;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/game-rounds")
@SecuredEndpoint
@Slf4j
public class GameRoundController extends AbstractReadOnlyEntityCurdController<GameRound> {

    @Autowired
    private GameRoundService service;

    @Autowired
    GameRequestHandler gameRequestHandler;

    @GetMapping("/{uid}/details")
    ResponseEntity<JsonNode> gameRoundDetails(@PathVariable String uid) {
        log.info("Player gameRound {} details requested", uid);

        GameRound gameRound = service.getStore().getGameRoundDetails(uid);
        if (gameRound == null) {
            throw new BaseRuntimeException(SystemErrorCode.NOT_FOUND);
        }

        JsonNode details = gameRequestHandler.gamePlayAndActivityDetails(gameRound);
        return ResponseEntity.ok(details);
    }
}
