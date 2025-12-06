package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.brandgames.BrandGame;
import aimlabs.gaming.rgs.brandgames.BrandGameService;
import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/brand-games")
@SecuredEndpoint
public class BrandGamesController extends AbstractEntityCurdController<BrandGame> {

    @Autowired
    private BrandGameService service;

    @GetMapping({ "/{brand}/games" })
    public List<BrandGame> getAllGames(@PathVariable("brand") String brand) {
        return service.findAllByBrand(brand);
    }

    @PostMapping({ "/{brand}/games" })
    public List<BrandGame> addGamesList(@PathVariable("brand") String brand, @RequestBody List<String> games) {
        service.addBrandGames(brand, games);
        return service.findAllByBrand(brand);
    }

    @GetMapping({ "/{id}/{status}" })
    public void updateGameStatus(@PathVariable("id") String id,
            @PathVariable("status") Status status) {
        service.updateStatus("id", status);
    }
}
