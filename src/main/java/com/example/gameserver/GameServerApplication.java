package com.example.gameserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Remote Game Server.
 * 
 * This application uses Spring Web MVC (spring-boot-starter-web) instead of
 * Spring WebFlux (spring-boot-starter-webflux). The key differences are:
 * 
 * - Spring Web MVC: Traditional blocking, servlet-based web framework
 * - Spring WebFlux: Reactive, non-blocking web framework
 * 
 * Both use similar annotations (@RestController, @GetMapping, etc.) but
 * Web MVC returns regular objects while WebFlux returns Mono/Flux types.
 */
@SpringBootApplication
public class GameServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameServerApplication.class, args);
    }
}
