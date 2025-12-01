package aimlabs.gaming.rgs.brandgames;


import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.games.GamePlayContext;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import aimlabs.gaming.rgs.gameskins.GameSkinService;
import lombok.Data;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Service
public class BrandGameService extends AbstractEntityService<BrandGame, BrandGameDocument> implements IBrandGameService {

    @Autowired
    BrandGameStore store;

    @Autowired
    BrandGameMapper mapper;

    @Autowired
    GameSkinService gameSkinService;

    @Autowired
    IBrandService brandService;

//    public void addBrandGames(String brand, List<String> games) {
//
//         store.addBrandGames(brand, games);
//    }


    @Cacheable
    public BrandGame findOneByTenantAndBrandAndGameSkin(String brand, String gameId) {
        return getMapper().asDto(store.findOneByBrandAndGameId(brand, gameId));
    }

//    public List<BrandGame> findAllByBrand(String brand) {
//        return getMapper().asDto(store.findAllByBrand(brand));
//    }

    @Override
    public List<BrandGame> findAllByNetwork(String network) {
        return store.findAllByNetwork(network);
    }

    @Override
    public BrandGameAggregate findOneByNetworkAndBrandAndGameId(String network, String brand, String gameId) {
        String tenant = TenantContextHolder.getTenant();
        Stream<Document> aggregationPipeline = Stream.of(new Document("$match",
                        new Document("brand", brand)
                                .append("game", gameId)
                                .append("tenant", tenant)
                                .append("deleted", false)),
                new Document("$lookup",
                        new Document("from", "Brands")
                                .append("let",
                                        new Document("brand_id", "$brand")
                                                .append("tenant_id", "$tenant")
                                                .append("deleted", "$deleted"))
                                .append("pipeline", List.of(new Document("$match",
                                        new Document("$expr",
                                                new Document("$and", Arrays.asList(
                                                        new Document("$eq", Arrays.asList("$$brand_id", "$uid")),
                                                        new Document("$eq", Arrays.asList("$$tenant_id", "$tenant")),
                                                        new Document("$eq", Arrays.asList("$$deleted", "$deleted"))
                                                ))))))
                                .append("as", "brand")),
                new Document("$unwind",
                        new Document("path", "$brand")),
                new Document("$lookup",
                        new Document("from", "Games")
                                .append("let",
                                        new Document("game_id", "$game")
                                                .append("tenant_id", "$tenant")
                                                .append("deleted", "$deleted"))
                                .append("pipeline", Arrays.asList(new Document("$match",
                                        new Document("$expr",
                                                new Document("$and", Arrays.asList(
                                                        new Document("$eq", Arrays.asList("$$game_id", "$uid")),
                                                        new Document("$eq", Arrays.asList("$$tenant_id", "$tenant")),
                                                        new Document("$eq", Arrays.asList("$$deleted", "$deleted"))
                                                ))))))
                                .append("as", "game")),
                new Document("$unwind",
                        new Document("path", "$game")),
                new Document("$project",
                        new Document("brand", 1L)
                                .append("game", 1L)
                                .append("network", 1L)
                                .append("status", 1L)
                                .append("tenant", 1L)
                                .append("_id", 0L)));

        List<AggregationOperation> queryGameProvidersAggregation =
                aggregationPipeline
                        .map(document -> (AggregationOperation) context -> document).collect(Collectors.toList());


        AggregationResults<BrandGameAggregate> result = store.getTemplate().aggregate(Aggregation.newAggregation(queryGameProvidersAggregation), "BrandGames", BrandGameAggregate.class);
        return !result.getMappedResults().isEmpty()? result.getMappedResults().getFirst():null;
    }
}
