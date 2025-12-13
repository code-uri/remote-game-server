package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.admin.ForcedResult;
import aimlabs.gaming.rgs.admin.ForcedResultRepository;
import aimlabs.gaming.rgs.admin.dto.ForcedResultsRequest;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;

//@ConditionalOnBean(RedisTemplate.class)
@RestController
@RequestMapping("/admin/forced-results")
@Slf4j
public class ForcedResultController implements CommandLineRunner {

    @Autowired
    ForcedResultRepository forcedResultRepository;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    @PostMapping
    public ResponseEntity<ForcedResult> save(@RequestBody ForcedResultsRequest request) {
        Objects.requireNonNull(request.getGameId());

        ForcedResult forcedResult = ForcedResult.createInstance(Objects.requireNonNull(redisTemplate.opsForValue()
                .increment("forced-results:counter")), request.getGameId(),
                request.getSession(), request.getGamePlay(), request.getBonus(), request.getReel());
        request.getJson().put("bonus", request.getBonus() != null ? request.getBonus() : "NONE");
        forcedResult.setJson(request.getJson());
        forcedResult = forcedResultRepository.save(forcedResult);
        return ResponseEntity.ok(forcedResult);
    }

    @GetMapping("/:id")
    public ResponseEntity<ForcedResult> find(@RequestParam(required = true) String id) {
        Optional<ForcedResult> forcedResult = forcedResultRepository.findById(id);
        ResponseEntity.of(forcedResult);
        return null;
    }

    @DeleteMapping("/:id")
    public ResponseEntity delete(@RequestParam(required = true) String id) {
        forcedResultRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<Page<ForcedResult>> findAllPaged(@RequestParam String size,
            @RequestParam String page) {

        PageRequest pageRequest = PageRequest.of(0, 20, Sort.Direction.DESC, "id");

        if (size != null && page != null) {
            pageRequest = PageRequest.of(Integer.parseInt(page), Integer.parseInt(size), Sort.Direction.DESC, "id");
        }
        Page<ForcedResult> results = forcedResultRepository.findAll(pageRequest);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ForcedResult>> searchPaged(@RequestParam String gameId,
            @RequestParam String session,
            @RequestParam String gamePlay,
            @RequestParam String size,
            @RequestParam String page) {

        PageRequest pageRequest = PageRequest.of(0, 20, Sort.Direction.ASC, "id");

        if (size != null && page != null) {
            pageRequest = PageRequest.of(Integer.parseInt(page), Integer.parseInt(size), Sort.Direction.ASC, "id");
        }
        Page<ForcedResult> results = null;
        if (gameId == null && session == null && gamePlay == null) {
            if (page != null && size != null)
                PageRequest.of(Integer.parseInt(page), Integer.parseInt(size), Sort.Direction.DESC, "id");

            return ResponseEntity.ok(forcedResultRepository.findAll(pageRequest));
        }
        /*
         * else if (gameId != null && session != null && gamePlay != null)
         * return
         * ResponseEntity.ok(forcedResultRepository.findByGameSkinAndSessionAndGamePlay(
         * gameId, session, gamePlay, pageRequest));
         * else if (gameId != null && session != null)
         * return
         * ResponseEntity.ok(forcedResultRepository.findByGameSkinAndSession(gameId,
         * session, pageRequest));
         * else
         * return ResponseEntity.ok(forcedResultRepository.findByGameSkin(gameId,
         * pageRequest));
         */
        return null;
    }

    @Override
    public void run(String... args) throws Exception {

        Boolean forcedResultCounter = redisTemplate.opsForValue().setIfAbsent("forced-results:counter", "0");
        if (forcedResultCounter == Boolean.TRUE) {
            log.info("Counter 'forced-results:counter' set : {}", forcedResultCounter);
        }
        log.info("Counter 'forced-results:counter' value : {}",
                redisTemplate.opsForValue().get("forced-results:counter"));

    }
}
