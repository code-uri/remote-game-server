# remote-game-server

A Remote Game Server implementation using **Spring Web API (MVC)** - the traditional blocking equivalent of Spring WebFlux.

## Overview

This project demonstrates a REST API for a remote game server built with Spring Web MVC (`spring-boot-starter-web`). This is the **synchronous/blocking** equivalent of a Spring WebFlux (`spring-boot-starter-webflux`) reactive implementation.

## Spring WebFlux vs Spring Web MVC

| Feature | Spring WebFlux | Spring Web MVC (This Project) |
|---------|---------------|-------------------------------|
| Programming Model | Reactive (non-blocking) | Traditional (blocking) |
| Return Types | `Mono<T>`, `Flux<T>` | Regular objects, `List<T>` |
| Dependency | `spring-boot-starter-webflux` | `spring-boot-starter-web` |
| Server | Netty (default) | Tomcat (default) |
| Use Case | High concurrency, streaming | Traditional CRUD applications |

### Code Comparison

**Spring WebFlux (Reactive):**
```java
@GetMapping
public Flux<Game> getAllGames() {
    return gameService.getAllGames();
}

@GetMapping("/{id}")
public Mono<ResponseEntity<Game>> getGameById(@PathVariable String id) {
    return gameService.getGameById(id)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
}
```

**Spring Web MVC (This Implementation):**
```java
@GetMapping
public ResponseEntity<List<Game>> getAllGames() {
    List<Game> games = gameService.getAllGames();
    return ResponseEntity.ok(games);
}

@GetMapping("/{id}")
public ResponseEntity<Game> getGameById(@PathVariable String id) {
    Game game = gameService.getGameById(id);
    if (game != null) {
        return ResponseEntity.ok(game);
    }
    return ResponseEntity.notFound().build();
}
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/games` | Get all games |
| GET | `/api/games/{id}` | Get a specific game |
| POST | `/api/games` | Create a new game |
| PUT | `/api/games/{id}` | Update a game |
| DELETE | `/api/games/{id}` | Delete a game |
| POST | `/api/games/{gameId}/join` | Join a game |
| GET | `/api/games/{gameId}/players` | Get players in a game |
| POST | `/api/games/{gameId}/start` | Start a game |
| POST | `/api/games/{gameId}/end` | End a game |

## Building and Running

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build
```bash
mvn clean package
```

### Run
```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080`.

### Test
```bash
mvn test
```

## Example Usage

### Create a new game
```bash
curl -X POST http://localhost:8080/api/games \
  -H "Content-Type: application/json" \
  -d '{"name": "My Game", "maxPlayers": 4}'
```

### Join a game
```bash
curl -X POST http://localhost:8080/api/games/{gameId}/join \
  -H "Content-Type: application/json" \
  -d '{"name": "Player1"}'
```

### Start a game
```bash
curl -X POST http://localhost:8080/api/games/{gameId}/start
```

## Project Structure

```
src/main/java/com/example/gameserver/
├── GameServerApplication.java      # Main application class
├── controller/
│   └── GameController.java         # REST controller (Web MVC)
├── model/
│   ├── Game.java                   # Game model
│   └── Player.java                 # Player model
└── service/
    └── GameService.java            # Business logic service
```
