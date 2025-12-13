package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerService;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@RestController
@RequestMapping("/admin/players")
@SecuredEndpoint
public class PlayersController extends AbstractReadOnlyEntityCurdController<Player> {

    @Autowired
    private PlayerService service;

}
