package in.aimlabs.gaming.gconnect.parimatch.controller;

import in.aimlabs.gaming.rgs.gameskin.GameLaunchRequest;
import in.aimlabs.gaming.dto.LaunchMode;
import in.aimlabs.gaming.services.IGameLaunchService;
import aimlabs.gaming.rgs.core.utils.TapOnNextSignalListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

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
    public Mono<ResponseEntity<?>> parimatchGameLaunch(
            @RequestParam(value = "sessionToken", required = false) String token,
            @RequestParam(value = "productId", required = true) String gameId,
            ServerHttpRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {

        if (token == null)
            token = "demo-" + System.currentTimeMillis();
        Map<String, String> queryParams = new HashMap<>(request.getQueryParams().toSingleValueMap());
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

        return gameLaunchService
                .launchGame(launchRequest)
                // .map(uri -> {
                // String gameUrlPath = uri.toString();
                //
                // if(gameUrlPath.startsWith("/")){
                // gameUrlPath = "https://"+finalRemoteHost + gameUrlPath;
                // }
                // else if(!gameUrlPath.startsWith("http")){
                // gameUrlPath = "https://"+finalRemoteHost +"/"+ gameUrlPath;
                // }
                //
                // return URI.create(gameUrlPath);
                // })
                .tap(() -> new TapOnNextSignalListener<URI>() {

                    public void doOnNext(URI location) throws Throwable {
                        log.info("Parimatch Game launch url: {}", location);
                    }
                })
                .map(location -> {
                    // location = URI.create(UriUtils.encode(location.toString(),
                    // StandardCharsets.UTF_8));
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
                })
                .checkpoint("Parimatch game launch request.", true);

    }

    private String getRemoteIPAddress(ServerHttpRequest request) {
        String remoteAddress = request.getHeaders().getFirst("X-Forwarded-For");
        if (remoteAddress == null)
            return request.getRemoteAddress().getAddress().getHostAddress();
        if (remoteAddress.contains(","))
            return remoteAddress.split(",")[0];

        return remoteAddress;
    }
}