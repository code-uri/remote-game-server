package aimlabs.gaming.rgs.brandgames;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import aimlabs.gaming.rgs.gameskins.GameSkinDocument;
import aimlabs.gaming.rgs.gameskins.GameSkinStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@Slf4j
@Service
public class BrandGameStore extends MongoEntityStore<BrandGameDocument> {

    @Autowired
    GameSkinStore gameSkinStore;

//    public void addBrandGames( String brand, List<String> games){
//       if(CollectionUtils.isEmpty(games)
//          //&& !StringUtils.hasText(connector)
//         ){
//           throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR, "Request should contain either gaming or connector");
//       }
//        Flux<BrandGameDocument> as = findAllByBrand(brand);
//                .map(BrandGameDocument::getGame)
//                .collectList()
//                .flatMap( existingGames -> {
//
//                    return Flux.fromIterable(games)
//                            .filter(game -> !existingGames.contains(game))
//                            .flatMap(game -> getBrandGameDocument( brand,  game))
//                            .collectList()
//                            .flatMapMany(brandGameDocuments -> {
//                                return getTemplate().insertAll(brandGameDocuments)
//                                        .thenMany(findAllByBrand( brand));
//                            })
//                            .collectList()
//                            .flatMapMany(updatedGamesList -> {
//                                //TODO fix this.
//                                List<String> deleteList = updatedGamesList.stream().filter(existingGame
//                                        -> !games.contains(existingGame.getGame())).map(EntityDocument::getId).toList();
//                                return getTemplate().findAllAndRemove(Query.query(Criteria.where("id").in(deleteList).and("deleted").is(false)), BrandGameDocument.class);
//                            }).then();
//                });
//    }

    private  BrandGameDocument getBrandGameDocument( String brand,  String game) {

        GameSkinDocument gameSkinDocument = gameSkinStore.findOneByGame(game);

        if(gameSkinDocument==null)
                throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME, "Game "+game+" not found!");

        BrandGameDocument brandGameDocument = new BrandGameDocument();
        brandGameDocument.setTenant(gameSkinDocument.getTenant());
        brandGameDocument.setBrand(brand);
        brandGameDocument.setGame(game);
        //brandGameDocument.setConnector(connector);

        return brandGameDocument;
    }

    public List<BrandGameDocument> findAllByBrand( String brand) {
        return getTemplate().find(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false).and("brand").is(brand)), BrandGameDocument.class);
    }


    public  BrandGameDocument findOneByBrandAndGameId(String brand, String gameId) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false)
                .and("brand").is(brand)
                .and("game").is(gameId)), BrandGameDocument.class);

    }

    public List<BrandGame> findAllByNetwork(String network) {
        return  getTemplate().find(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false)
                .and("network").is(network)), BrandGame.class, "BrandGames");
    }
}