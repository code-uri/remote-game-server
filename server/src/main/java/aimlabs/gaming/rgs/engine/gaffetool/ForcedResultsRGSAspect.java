package aimlabs.gaming.rgs.engine.gaffetool;

import aimlabs.gaming.rgs.admin.ForcedResultRepository;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.GameEngineServiceAdaptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import in.aimlabs.gaming.engine.api.model.ForceGameResult;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.SortParameters;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.query.SortQuery;
import org.springframework.data.redis.core.query.SortQueryBuilder;
import org.springframework.util.CollectionUtils;


import java.util.List;

@Aspect
@Data
@Slf4j
public class ForcedResultsRGSAspect {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ForcedResultRepository resultRepository;


    @Autowired
    ForcedResultsRGSServiceDiscovery forcedResultsRGSServiceDiscovery;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    // Use StringRedisTemplate for SORT operations to avoid GenericJackson2JsonRedisSerializer trying to
    // deserialize non-JSON scalar responses (e.g. plain strings like 'wild').
    @Autowired
    StringRedisTemplate stringRedisTemplate;


    @Pointcut("execution(public * aimlabs.gaming.rgs.games.GameEngineServiceAdaptor.play(..))")
    protected void play() {
    }


    @Around("play()")
    private Object forceResult(ProceedingJoinPoint pjp) throws Throwable {

        String gameId = (String) pjp.getArgs()[0];
        String session = (String) pjp.getArgs()[1];
        String gameConfiguration = (String) pjp.getArgs()[2];
        JsonNode gamePlayNode = (JsonNode) pjp.getArgs()[4];
        String gamePlayUid = gamePlayNode.get("uid").asText();

        GameEngineServiceAdaptor rgsService = (GameEngineServiceAdaptor) pjp.getTarget();
        //GamePlay gamePlay = rgsService.getGamePlay(gameConfiguration, (ObjectNode) gamePlayNode).share().block(Duration.ofSeconds(2));

        ForceGameResult forceGameResult = forcedResultsRGSServiceDiscovery.getForceGameResult(gameConfiguration);

        if (forceGameResult == null) {
            log.info("ForceGameResult not found for {}", gameConfiguration);
        } else {
            log.info("Force results start. Session: {}, GameSkin: {}, GamePlayDTO: {}", session, gameId, gamePlayUid);

            try {

                String playingBonus = null;
                if(gamePlayNode.has("gamePlayState") && gamePlayNode.get("gamePlayState").has("bonusAwarded"))
                {
                    ArrayNode arrayNode = ((ArrayNode) gamePlayNode.get("gamePlayState").get("bonusAwarded"));
                    playingBonus = arrayNode.get(arrayNode.size()-1).asText();
                }else{
                    playingBonus = "NONE";
                }

                SortQuery<String> sortQuery = SortQueryBuilder.sort("forced-results:attributes:" + gameId + "-" + session + "-" + gamePlayUid + "-" + playingBonus)
                        .by("forced-results:*->id")
                        .get("forced-results:*->id")
                        .get("forced-results:*->attributes")
                        .get("forced-results:*->json")
                        .limit(0, 1)
                        .order(SortParameters.Order.ASC).alphabetical(true).build();


                // Use stringRedisTemplate so results are returned as raw strings instead of being deserialized as JSON objects
                List<String> forcedResults = stringRedisTemplate.sort(sortQuery);

                if (CollectionUtils.isEmpty(forcedResults)) {
                    sortQuery = SortQueryBuilder.sort("forced-results:attributes:" + gameId + "-" + session + "-" + playingBonus)
                            .by("forced-results:*->id")
                            .get("forced-results:*->id")
                            .get("forced-results:*->attributes")
                            .get("forced-results:*->json")
                            .limit(0, 1)
                            .order(SortParameters.Order.ASC).alphabetical(true).build();

                    forcedResults = stringRedisTemplate.sort(sortQuery);
                }

                if (CollectionUtils.isEmpty(forcedResults)) {//SORT "forced-results:attributes:itl-game" BY forced-results:*->id get forced-results:*->id ASC
                    sortQuery = SortQueryBuilder.sort("forced-results:attributes:" + gameId + "-" + playingBonus)
                            .by("forced-results:*->id")
                            .get("forced-results:*->id")
                            .get("forced-results:*->attributes")
                            .get("forced-results:*->json")
                            .limit(0, 1)
                            .order(SortParameters.Order.ASC).alphabetical(true).build();

                    forcedResults = stringRedisTemplate.sort(sortQuery);
                }

                //  log.info("Forcedresults {}", forcedResults);
                if (!CollectionUtils.isEmpty(forcedResults)) {
                    // forcedResults contains strings in the order get(...) were specified: id, attributes, json
                    if (forcedResults.size() < 3) {
                        log.warn("Unexpected SORT result length (expected >=3): {}. Key: forced-results:attributes:{}", forcedResults.size(), gameId);
                    } else {
                        String idValue = forcedResults.get(0);
                        String jsonValue = forcedResults.get(2);

                        JsonNode forcedResult;
                        try {
                            // try parse as JSON
                            forcedResult = objectMapper.readTree(jsonValue);
                        } catch (Exception parseEx) {
                            // fallback: value is a plain scalar (e.g. wild). Wrap as text node so downstream code can still work
                            log.warn("Failed to parse forced-result json (will use raw text node). Value={}", jsonValue);
                            forcedResult = objectMapper.getNodeFactory().textNode(jsonValue);
                        }

                        log.info("Forced result found {}", forcedResult);
                        //if (forceGameResult.applyForcedResult(forcedResult, gamePlay)) {
                        forceGameResult.getGamePlayUidToForcedResultMap().put(gamePlayUid, forcedResult);
                        resultRepository.deleteById(idValue);
                        log.info("Forced result: {} consumed", idValue);
                        //}
                    }
                }
            } catch (Exception e) {
                log.error("Force results failed. Session: {}, GameSkin: {}, GamePlayDTO: {}", session, gameId, gamePlayUid, e);
            }
               log.info("Force results end. Session: {}, GameSkin: {}, GamePlayDTO: {}", session, gameId, gamePlayUid);
        }

        try {
            return pjp.proceed();
        } catch (Throwable e) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, e);
        }
    }
}
