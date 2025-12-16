package aimlabs.gaming.rgs.brandgames;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import aimlabs.gaming.rgs.gameskins.GameSkinDocument;
import aimlabs.gaming.rgs.gameskins.GameSkinStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Slf4j
@Service
public class BrandGameStore extends MongoEntityStore<BrandGameDocument> {

    @Autowired
    GameSkinStore gameSkinStore;

    public void addBrandGames(String brand, List<String> games) {
        if (CollectionUtils.isEmpty(games)) {
            throw new BaseRuntimeException(SystemErrorCode.SYSTEM_ERROR,
                    "Request should contain either gaming or connector");
        }

        List<BrandGame> existingBrandGames = findAllByBrand(brand);
        List<String> existingGames = existingBrandGames.stream()
                .map(BrandGame::getGame)
                .collect(Collectors.toList());

        // Create new brand games for games that don't exist
        List<BrandGameDocument> newBrandGames = games.stream()
                .filter(game -> !existingGames.contains(game))
                .map(game -> getBrandGameDocument(brand, game))
                .collect(Collectors.toList());

        if (!newBrandGames.isEmpty()) {
            getTemplate().insertAll(newBrandGames);
        }

        // Find and delete games that are no longer in the list
        List<BrandGame> updatedGamesList = findAllByBrand(brand);
        List<String> deleteList = updatedGamesList.stream()
                .filter(existingGame -> !games.contains(existingGame.getGame()))
                .map(BrandGame::getId)
                .collect(Collectors.toList());

        if (!deleteList.isEmpty()) {
            getTemplate().remove(Query.query(Criteria.where("id").in(deleteList).and("deleted").is(false)),
                    BrandGameDocument.class);
        }
    }

    private BrandGameDocument getBrandGameDocument(String brand, String game) {

        GameSkinDocument gameSkinDocument = gameSkinStore.findOneByGame(game);

        if (gameSkinDocument == null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_GAME, "Game " + game + " not found!");

        BrandGameDocument brandGameDocument = new BrandGameDocument();
        brandGameDocument.setTenant(gameSkinDocument.getTenant());
        brandGameDocument.setBrand(brand);
        brandGameDocument.setGame(game);
        // brandGameDocument.setConnector(connector);

        return brandGameDocument;
    }

    public List<BrandGame> findAllByBrand(String brand) {
        return getTemplate().find(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false).and("brand").is(brand)), BrandGame.class);
    }

    public BrandGameDocument findOneByBrandAndGameId(String brand, String gameId) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false)
                .and("brand").is(brand)
                .and("game").is(gameId)), BrandGameDocument.class);

    }

    public List<BrandGame> findAllByNetwork(String network) {
        return getTemplate().find(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("deleted").is(false)
                .and("network").is(network)), BrandGame.class, "BrandGames");
    }
}