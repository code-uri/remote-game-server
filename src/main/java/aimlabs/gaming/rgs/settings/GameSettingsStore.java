package aimlabs.gaming.rgs.settings;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Data
@Slf4j
@Service
public class GameSettingsStore extends MongoEntityStore<GameSettingsDocument> {


    /*
    public Mono<GameSettingsDocument> findOneByUid(String uid) {
        return getTemplate()
                .findOne(Query.query(Criteria.where("uid").regex(uid, "i"))
                                .collation(Collation.of(Locale.ENGLISH)),
                        GameSettingsDocument.class);
    }
    */
    public List<String> getSettings(String tenant, String brand, String gameId) {

        MatchOperation matchOperation = match(where("deleted").is(false)
                .and("tenant").is(tenant)
                .and("brand").in(brand.toLowerCase(), null)
                .and("game").in(gameId.toLowerCase(), null));

        //SortOperation sortByGameAsc = sort(Sort.by(Sort.Direction.DESC, "game"));
        SortOperation sortByBrandAmdGameAsc = sort(Sort.by(Sort.Direction.ASC, "brand", "game"));

        Aggregation aggregation = Aggregation.newAggregation(matchOperation, sortByBrandAmdGameAsc);

        return getTemplate().aggregate(
                        aggregation, "GameSettings", GameSettingsDocument.class)
                .getMappedResults().stream()
                .flatMap(gameSettingsDocument -> gameSettingsDocument.getSettings()
                        .stream()).toList();

    }
}