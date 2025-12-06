package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.gameskins.GameSkinService;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/game-skins")
@SecuredEndpoint
public class GameSkinController extends AbstractEntityCurdController<GameSkin> {

    @Autowired
    private GameSkinService service;
}
