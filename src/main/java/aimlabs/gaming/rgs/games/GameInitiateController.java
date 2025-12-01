package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gamesessions.IGameSessionService;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.Player;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Slf4j
@RestController
@RequestMapping("/games")
public class GameInitiateController {


    @Autowired
    IPlayerService playerService;

    @Autowired
    IBrandService brandService;

    @Autowired
    IGameSessionService gameSessionService;

    @PostMapping("/init-session")
    Map<String, String> init(@RequestBody GameSessionRequest sessionRequest,
                             HttpServletRequest httpServletRequest) {

        Brand brand = brandService.findOneByTenantAndBrand(TenantContextHolder.getTenant(),
                sessionRequest.getBrand());

        if (brand == null) {
            throw new BaseRuntimeException(SystemErrorCode.NOT_FOUND);
        }

        GameSession request = new GameSession(brand.getTenant(),
                sessionRequest.getBrand(),
                sessionRequest.getPlayerId(),
                sessionRequest.getCurrency());
        request.setNetwork(brand.getNetwork());
        request.setToken(sessionRequest.getToken());
        request.setIpAddress(getRemoteIPAddress(httpServletRequest));

        Player player = playerService.registerOrUpdate(brand.getNetwork(), brand.getUid(), sessionRequest.getPlayerId(), List.of());

        request.setPlayer(player.getUid());
        request.setToken(UUID.randomUUID().toString());

        GameSession gameSession = gameSessionService.createGameSession(request);

        Map<String, String> map = new HashMap<>();
        map.put("launchToken", gameSession.getToken());
        return map;
    }

    private String getRemoteIPAddress(HttpServletRequest request) {
        String remoteAddress = request.getHeader("X-Forwarded-For");
        if (remoteAddress == null)
            return request.getRemoteAddr();
        if (remoteAddress.contains(","))
            return remoteAddress.split(",")[0];

        return remoteAddress;
    }

    private String getTenant(HttpServletRequest request) {
        String remoteHost = request.getHeader("X-Forwarded-Host");
        if (remoteHost == null)
            return request.getRemoteHost();
        if (remoteHost.contains(","))
            return remoteHost.split(",")[0];

        if (remoteHost.contains(".mplaygames.com"))
            return "mplay";
        else
            return "default";
    }
}

