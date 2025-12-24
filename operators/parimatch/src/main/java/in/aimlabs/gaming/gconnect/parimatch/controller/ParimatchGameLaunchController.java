package in.aimlabs.gaming.gconnect.parimatch.controller;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameskins.GameLaunchRequest;
import aimlabs.gaming.rgs.gameskins.IGameLaunchService;
import aimlabs.gaming.rgs.gameskins.LaunchMode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

@Data
@Slf4j
@RestController
@RequestMapping("/games/launch")
public class ParimatchGameLaunchController {

    @Value("${rgs.player.connector.parimatch.partner:parimatch}")
    String network;

    @Autowired
    private IGameLaunchService gameLaunchService;

    @PostConstruct
    void init() {
        log.info("GameLaunchController. initiated.");
    }

    // @Autowired
    // GameSkinService gameSkinService;

    @GetMapping(value = "")
    public ResponseEntity<?> parimatchGameLaunch(
            @RequestParam(value = "sessionToken", required = false) String token,
            @RequestParam(value = "productId", required = true) String gameId,
            HttpServletRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        if (token == null)
            token = "demo-" + System.currentTimeMillis();

        Map<String, String> queryParams = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                queryParams.put(key, values[0]);
            }
        });
        queryParams.put("token", token);
        GameLaunchRequest launchRequest = new GameLaunchRequest(null, network, "parimatch", token, gameId, queryParams);
        launchRequest.setIpAddress(getRemoteIPAddress(request));

        // String remoteHost = request.getHeaders().getFirst("X-Forwarded-Host");
        // if (remoteHost == null)
        // remoteHost = request.getRemoteAddress().getAddress().getHostName();
        // if (remoteHost.contains(","))
        // remoteHost = remoteHost.split(",")[0];
        //
        // String finalRemoteHost = remoteHost;

        URI location = gameLaunchService.launchGame(launchRequest);
        if (location == null) {
            throw new BaseRuntimeException(SystemErrorCode.GAME_COMING_SOON);
        }
        log.info("Parimatch Game launch url: {}", location);

        if (LaunchMode.REDIRECT == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        } else if (LaunchMode.EMBEDDED == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .location(location)
                    .build();
        } else if (LaunchMode.JSON == launchRequest.getMode()) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(location.toString());
        } else {
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        }

    }

    private String getRemoteIPAddress(HttpServletRequest request) {
        String remoteAddress = request.getHeader("X-Forwarded-For");
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return request.getRemoteAddr();
        }
        if (remoteAddress.contains(",")) {
            return remoteAddress.split(",")[0].trim();
        }

        return remoteAddress.trim();
    }
}