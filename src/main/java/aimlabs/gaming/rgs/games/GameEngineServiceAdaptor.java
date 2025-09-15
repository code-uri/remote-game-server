package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.engine.discovery.RGSServiceDiscovery;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.aimlabs.gaming.engine.api.bonus.BonusContext;
import in.aimlabs.gaming.engine.api.bonus.GameBonus;
import in.aimlabs.gaming.engine.api.model.*;
import in.aimlabs.gaming.engine.api.module.GameEngineModule;
import in.aimlabs.gaming.engine.api.service.GameEngine;
import in.aimlabs.gaming.engine.api.symbol.Symbol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GameEngineServiceAdaptor {


    private final ConcurrentHashMap<String, ObjectMapper> gameObjectMappers = new ConcurrentHashMap<>();

    @Autowired
    RGSServiceDiscovery rgsServiceDiscovery;
    @Autowired
    ObjectMapper objectMapper;


    public ObjectNode play(String gameSkin, String session, String gameConfiguration,
                                 ObjectNode request,
                                 ObjectNode gamePlayJsonNode,
                                 ObjectNode previousGameActivity,
                                 Object stakeSettings) {

        GameEngine<GameEngineRequest, GameEngineResponse> gameEngineService =
                rgsServiceDiscovery.getEngineService(gameConfiguration);
        GameEngineModule gameEngineModule = gameEngineService.getGameEngineModule();
        Class<GameEngineRequest> requestClass = gameEngineModule.getSubType(GameEngineRequest.class);
        GameEngineRequest gameEngineRequest = objectMapper.convertValue(request, requestClass);

        GamePlay gamePlay = constructGamePlay(gameConfiguration, gamePlayJsonNode);
        log.info("Game Play gameId {}", gamePlay.getGameId());
        GameActivity gameActivity = constructGameActivity(gameConfiguration, previousGameActivity);
        GameEngineResponse response = gameEngineService.play(gameEngineRequest, gamePlay, gameActivity, (StakeSettings) stakeSettings);
        response.getGamePlay().setGameId(gameSkin);
        return objectMapper.valueToTree(response);
    }


    public ObjectNode ack(String gameConfiguration,
                                ObjectNode request,
                                ObjectNode gamePlayJsonNode,
                                ObjectNode gameActivityJsonNode) {

            GameEngine<GameEngineRequest, GameEngineResponse> gameEngineService =
                    rgsServiceDiscovery.getEngineService(gameConfiguration);
            GameEngineModule gameEngineModule = rgsServiceDiscovery.getRegisteredGameEngineModule(gameConfiguration);
            Class<GameEngineRequest> requestClass = gameEngineModule.getSubType(GameEngineRequest.class);
            GameEngineRequest gameEngineRequest = objectMapper.convertValue(request, requestClass);
            GamePlay gamePlay = constructGamePlay(gameConfiguration, gamePlayJsonNode);
            GameActivity gameActivity = constructGameActivity(gameConfiguration, gameActivityJsonNode);
            Object response = gameEngineService.ack(gameEngineRequest, gamePlay, gameActivity);
            return objectMapper.valueToTree(response);

    }

    public ObjectNode prepareGameClientResponse(String gameConfiguration,
                                                      ObjectNode gamePlayJsonNode,
                                                      ObjectNode gameActivityJsonNode) {

            GameEngine<GameEngineRequest, GameEngineResponse> gameEngineService =
                    rgsServiceDiscovery.getEngineService(gameConfiguration);
            GamePlay gamePlay = constructGamePlay(gameConfiguration, gamePlayJsonNode);
            GameActivity gameActivity = constructGameActivity(gameConfiguration, gameActivityJsonNode);
            GameEngineResponse response = gameEngineService.prepareGameClientResponse(gamePlay, gameActivity);
            return objectMapper.valueToTree(response);

    }

    private GameActivity constructGameActivity(String gameConfiguration, ObjectNode content) {
        return registerAndCacheObjectMapper(gameConfiguration)
                .convertValue(content, GameActivity.class);
    }


    private GamePlay constructGamePlay(String gameConfiguration, ObjectNode content) {

        return registerAndCacheObjectMapper(gameConfiguration)
                .convertValue(content, GamePlay.class);
    }

    private ObjectMapper registerAndCacheObjectMapper(String gameConfiguration) {
        ObjectMapper om = this.gameObjectMappers.get(gameConfiguration);
        if (om != null)
            return om;

//        synchronized (this) {
        GameEngineModule gameEngineModule = rgsServiceDiscovery.getRegisteredGameEngineModule(gameConfiguration);
        SimpleModule module = new SimpleModule(gameConfiguration);

        module.addKeyDeserializer(GameBonus.class, new KeyDeserializer() {

            public Object deserializeKey(String key, DeserializationContext ctxt) {
                @SuppressWarnings({"unchecked", "rawtypes"}) Class<? extends Enum> gameBonusClass = (Class<? extends Enum>) gameEngineModule.getSubTypesMap().get(GameBonus.class);

                //noinspection unchecked
                return Enum.valueOf(gameBonusClass, key);
            }
        });

        Class<? extends GamePlayState> gamePlayStateClass = gameEngineModule.getSubType(GamePlayState.class);
        if (gamePlayStateClass != null) {
            module.addAbstractTypeMapping(GamePlayState.class, gamePlayStateClass);
        }

        Class<? extends GameActivity> gameActivityClass = gameEngineModule.getSubType(GameActivity.class);

        if (gameActivityClass != null)
            module.addAbstractTypeMapping(GameActivity.class, gameActivityClass);

        Class<? extends Symbol> symbolClass = gameEngineModule.getSubType(Symbol.class);
        if (symbolClass != null)
            module.addAbstractTypeMapping(Symbol.class, symbolClass);

        Class<? extends BonusContext> bonusContextClass = gameEngineModule.getSubType(BonusContext.class);
        if (bonusContextClass != null)
            module.addAbstractTypeMapping(BonusContext.class, bonusContextClass);

        Class<? extends GameBonus> gameBonusClass = gameEngineModule.getSubType(GameBonus.class);
        if (gameBonusClass != null)
            module.addAbstractTypeMapping(GameBonus.class, gameBonusClass);

            /*Class<KeyDeserializer> keyDeserializerClass = gameEngineModule.getSubType(KeyDeserializer.class);
                module.addKeyDeserializer(keyDeserializerClass);*/

        this.gameObjectMappers.put(gameConfiguration, this.objectMapper.copy().registerModule(module));
        //   }
        return this.gameObjectMappers.get(gameConfiguration);
    }


    public ObjectNode getGameClientConfig(String gameConfiguration) {
        GameEngine<GameEngineRequest, GameEngineResponse> gameEngineService =
                rgsServiceDiscovery.getEngineService(gameConfiguration);
        return gameEngineService.getGameClientConfig(gameConfiguration);
    }


    public Object prepareGameClientResponse(GamePlay gamePlay, GameActivity previousGameActivity) {
        return null;
    }


    public GameEngineModule getGameEngineModule() {
        return null;
    }


/*
    @Override
    public Mono<GamePlay> getGamePlay(String gameConfiguration, ObjectNode gamePlayNode) {
        return Mono.just(constructGamePlay(gameConfiguration, gamePlayNode));
    }
*/


}
