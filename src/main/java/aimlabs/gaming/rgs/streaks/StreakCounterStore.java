package aimlabs.gaming.rgs.streaks;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.ConditionalOperators.IfNull.ifNull;

@Data
@Slf4j
@Service
public
class StreakCounterStore extends MongoEntityStore<StreakCounterDocument> {


    public StreakCounterDocument findActiveStreak(String gameId, String player, String currency) {

        return getTemplate()
                .find(Query.query(Criteria.where("deleted").is(false)
                                .and("tenant").is(TenantContextHolder.getTenant())
                                .and("player").is(player)
                                .and("game").is(gameId)
                                .and("currency").is(currency)
                                .and("status").is(Status.INPROGRESS)
                        ).with(Sort.by(Sort.Direction.DESC, "_id")),
                        StreakCounterDocument.class).stream().findFirst().orElse(null);

    }


    public StreakCounter incrementStreakForPlayer(String player,
                                                        String game,
                                                        String startGameRoundId,
                                                        String lastGameResult,
                                                        Double streakWager,
                                                        String currency,
                                                        List<Double> streakMultipliers,
                                                        int increment) {


        Query query = Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("player").is(player)
                .and("deleted").is(false)
                .and("game").is(game)
                .and("status").is(Status.INPROGRESS)
        );


        // Define the aggregation pipeline update
        AggregationUpdate updatePipeLine = AggregationUpdate.update()
                .set("player").toValue(player)
                .set("game").toValue(game)
                .set("deleted").toValue(false)
                .set("lastGameResult").toValue(lastGameResult)
                .set("streakMultipliers").toValue(streakMultipliers)
                .set("status").toValue(Status.INPROGRESS)
                .set("startGameRoundId").toValueOf( ifNull("startGameRoundId").then(startGameRoundId))
                .set("endGameRoundId").toValue(null)
                .set("streakWager").toValue(streakWager)
                .set("currency").toValue(currency)
                .set("streak").toValueOf(
                        ConditionalOperators.Cond
                                .when(ComparisonOperators.Lt
                                        .valueOf(ifNull("streak").then(0))
                                        .lessThanValue(streakMultipliers.size()))
                                .thenValueOf(ArithmeticOperators.Add
                                        .valueOf(ifNull("streak").then(0))
                                        .add(increment))
                                .otherwiseValueOf("streak")
                )
                .set("bonusMultiplier").toValueOf(ArrayOperators.ArrayElemAt.arrayOf("streakMultipliers")
                        .elementAt( ArithmeticOperators.Subtract.valueOf(ConditionalOperators.Cond
                                        .when(ComparisonOperators.Gt.valueOf("streak").greaterThanValue(0))
                                        .thenValueOf("streak")
                                        .otherwise(1))
                                .subtract(1)));

        return getTemplate().findAndModify(query, updatePipeLine,
                FindAndModifyOptions.options().upsert(true).returnNew(false), StreakCounter.class, "StreakCounters");
    }

    public StreakCounter endStreak(String player, String game,
                                         String endGameRoundId,
                                         String lastGameResult, List<Double> streakMultipliers) {


        Query query = Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                .and("player").is(player)
                .and("deleted").is(false)
                .and("game").is(game)
                .and("status").is(Status.INPROGRESS));

        Update update = new Update();
        update.set("status", Status.COMPLETED);
        update.set("endGameRoundId", endGameRoundId);


        AggregationUpdate updatePipeLine = AggregationUpdate.update()
                .set("status").toValue(Status.COMPLETED)
                .set("endGameRoundId").toValue(endGameRoundId)
                .set("streakWin")
                .toValueOf(ArithmeticOperators.Multiply.valueOf("streakWager")
                        .multiplyBy( "bonusMultiplier"));

        return getTemplate().findAndModify(query, updatePipeLine, FindAndModifyOptions.options()
                        .upsert(false).returnNew(true),
                StreakCounter.class, "StreakCounters");
    }
}