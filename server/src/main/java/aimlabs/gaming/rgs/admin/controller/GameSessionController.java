package aimlabs.gaming.rgs.admin.controller;

import lombok.Data;
import aimlabs.gaming.rgs.admin.controller.AbstractReadOnlyEntityCurdController;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/sessions")
@SecuredEndpoint
public class GameSessionController extends AbstractReadOnlyEntityCurdController<GameSession> {

    @Autowired
    private IGameSessionService service;

}
