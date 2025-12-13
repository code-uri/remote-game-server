package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.utils.ObjectMapperUtils;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.transactions.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import org.bson.Document;
import in.aimlabs.gaming.engine.api.model.GameStatus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;


@Data
@Slf4j
@Service
public class GameRoundStore extends MongoEntityStore<GameRoundDocument> {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    private GameRoundMapper mapper;



    public GameRound fetchUnfinishedGames(String player, String gameId, String gamePlayUid) {
        return  getMapper().asDto(getTemplate().findOne(Query.query(where("player").is(player)
                .and("gameId").is(gameId)
                .and("gamePlay").is(gamePlayUid)
                .and("status").is("INPROGRESS")).with(Sort.by(Sort.Direction.DESC, "id")), GameRoundDocument.class));
    }


    public GameRound updateGameRound(GameRound gameRound) {

        return getMapper().asDto(getTemplate()
                .findAndModify(Query.query(where("uid").is(gameRound.getUid())),
                        new Update()
                                .set("modifiedOn", new Date())
                                .set("status", gameRound.getStatus()),
                        //.set("jackpotDetails", gameRound.getJackpotDetails()),
                        GameRoundDocument.class, "GameRounds"));

    }

    public GameRound addTransaction(String gameRoundUid, Transaction transaction) {

        Update update = Update.update("modifiedOn", new Date());
        update.push("transactions", transaction.getUid());
        update.set("wallet", transaction.getWallet());
        if (transaction.getDebit() != null && transaction.getDebit().isPositive()) {
            update.inc("totalWager.amount", transaction.getDebit().getNumber().doubleValue());
        }
        if (transaction.getCredit() != null && transaction.getCredit().isPositive()) {
            update.inc("totalWin.amount", transaction.getCredit().getNumber().doubleValue());
        }

        return getMapper().asDto(getTemplate()
                .findAndModify(Query.query(where("uid").is(gameRoundUid)
                                .and("transactions").ne(transaction.getUid())),
                        update,
                        GameRoundDocument.class, "GameRounds"));
    }


    public List<GameRound> findAutoPlayCandidates(int hrs) {
        return  getTemplate()
                .find(Query.query(where("modifiedOn")
                                .lt(LocalDateTime.now().minus(Duration.ofHours(hrs)))
                                .and("status").is("INPROGRESS")
                                .and("autoPlayable").is(true))
                        , GameRoundDocument.class)
                .stream()
                .map(gameRoundDocument -> getMapper().asDto(gameRoundDocument))
                .toList();
    }

    public List<GameRound> fetchPlayerGameHistory(String player, String gameSkin, int page, int size) {
        return getTemplate().find(Query.query(where("player").is(player)
                .and("gameId").is(gameSkin)).with(Pageable.ofSize(size).withPage(page)), GameRound.class, "GameRounds");
    }

    public GameRound getGameRoundDetails(String uid) {
        return getTemplate().findOne(Query.query(where("uid").is(uid).and("status").is(Status.COMPLETED)), GameRound.class, "GameRounds");
    }


    public UnfinishedGame findUnfinishedGames(String player, GameSkin gameSkin, boolean demo, boolean confirmHand) {


        if (gameSkin.getGameType().equals("BOARD") ) {
            return findUnfinishedGamesForBoardGames(player, gameSkin.getUid(), demo, confirmHand);
        }
        else
            return getUnfinishedGameRounds(player, gameSkin.getUid(), demo, confirmHand, null);
    }

    private UnfinishedGame getUnfinishedGameRounds(String player, String gameId, boolean demo, boolean confirmHandSupported, String gamePlay) {
        Criteria criteria = where("player").is(player)
                .and("gameId").is(gameId)
                .and("demo").is(demo);

        if (gamePlay != null)
            criteria.and("gamePlay").is(gamePlay);

        if (confirmHandSupported) {
            Criteria gamePlayStatusCriteria = new Criteria();
            if (gamePlay != null)
                gamePlayStatusCriteria.and("status").in(GameStatus.INPROGRESS.name(), GameStatus.COMPLETED);
            else
                gamePlayStatusCriteria.and("status").in(GameStatus.INPROGRESS.name());

            criteria.orOperator(where("handConfirmed").is(false),
                    gamePlayStatusCriteria);
        } else if (gamePlay != null)
            criteria.and("status").in(GameStatus.INPROGRESS.name(), GameStatus.COMPLETED);
        else
            criteria.and("status").in(GameStatus.INPROGRESS.name());

        //TODO FIX this for BOARD GAMES criteria.and("status").in(GameStatus.INPROGRESS.name(), GameStatus.COMPLETED);


       /* Document match = new Document("player", "DEMO-forever123")
                .append("gameId", "amazingatlantis")
                .append("demo", true);

        if(confirmHand)
            match.append("handConfirmed", true);
        match.append("status", "INPROGRESS");
*/
        Document sortOperation = null;
        if (confirmHandSupported)
            sortOperation = new Document("$sort",
                    new Document("handConfirmed", 1L));
        else if (gamePlay != null)
            sortOperation = new Document("$sort",
                    new Document("_id", -1L));
        else
            sortOperation = new Document("$sort",
                    new Document("_id", 1L));

        List<AggregationOperation> aggregationOperations = Stream.of(
                sortOperation,
                new Document("$project",
                        new Document("_id", 0L)
                                .append("gameRound", "$$ROOT")),
                new Document("$lookup",
                        new Document("from", "GamePlays")
                                .append("let",
                                        new Document("game_play", "$gameRound.gamePlay"))
                                .append("pipeline", List.of(new Document("$match",
                                        new Document("$expr",
                                                new Document("$eq", Arrays.asList("$uid", "$$game_play"))))))
                                .append("as", "gamePlay")),
                new Document("$project",
                        new Document("gameRound", 1L)
                                .append("gamePlay",
                                        new Document("$last", "$gamePlay"))),
                new Document("$lookup",
                        new Document("from", "GameActivities")
                                .append("let",
                                        new Document("game_play", "$gamePlay.uid"))
                                .append("pipeline", List.of(new Document("$match",
                                        new Document("$expr",
                                                new Document("$eq", Arrays.asList("$gamePlay", "$$game_play"))))))
                                .append("as", "gameActivity")),
                new Document("$project",
                        new Document("gameRound", 1L)
                                .append("gamePlay", 1L)
                                .append("gameActivity",
                                        new Document("$last", "$gameActivity"))),
                new Document("$match",
                        new Document("gameActivity",
                                new Document("$exists", true))),
                new Document("$limit", 1L))
                .map(document -> (AggregationOperation) context -> document)
                .toList();

        ArrayList<AggregationOperation> operations = new ArrayList<>();
        operations.add(match(criteria));
        operations.addAll(aggregationOperations);
        return getTemplate().aggregate(newAggregation(operations), "GameRounds", UnfinishedGame.class)
                .getMappedResults().getFirst();
    }

    private UnfinishedGame findUnfinishedGamesForBoardGames(String player, String gameId, boolean demo, boolean confirmhandSupported) {
        Criteria criteria = where("player").is(player)
                .and("gameId").is(gameId)
                .and("demo").is(demo)
                .and("status").in(GameStatus.INPROGRESS.name())
                .and("deleted").is(false);

        Object dbObject = getTemplate().findOne(Query.query(criteria),
                DBObject.class, "GamePlays");

        JsonNode gamePlayJsonNode = ObjectMapperUtils.convertToJsonNode(objectMapper, dbObject);
        UnfinishedGame unfinishedGame = getUnfinishedGameRounds(player, gameId, demo, confirmhandSupported, gamePlayJsonNode.get("uid").asText());

        if (!confirmhandSupported && unfinishedGame.getGameRound().getStatus() == Status.COMPLETED)
            unfinishedGame.setGameRound(null);


        return unfinishedGame;
    }

    public GameRound findOneByTenantAndGameIdAndCorrelationId(String tenant, String gameId, String correlationId) {
        return getTemplate().findOne(Query.query(where("tenant").is(tenant).and("gameId").is(gameId).and("correlationId").is(correlationId).and("deleted").is(false)), GameRound.class, "GameRounds");
    }

    public Boolean isPendingRoundExists(String player, String game) {
        return getTemplate().exists(Query.query(where("gameId").is(game)
                .and("player").is(player)
                .and("status").is("INPROGRESS")
                .and("demo").is(false)
        ).limit(1), GameRoundDocument.class);
    }


    public UnfinishedGame getLastGameRoundWithDetails(String playerId, String gameId, String tenant, String currency) {
        // Match stage for GameRounds
        MatchOperation matchGameRound = match(where("player").is(playerId)
                .and("gameId").is(gameId)
                .and("tenant").is(tenant)
                .and("status").in("INPROGRESS", "COMPLETED")
                .and("totalWager.currency").is(currency));

        // Lookup stage for GamePlays
        AggregationOperation lookupGamePlay = context -> new Document("$lookup",
                new Document("from", "GamePlays")
                        .append("let", new Document("gamePlay", "$gamePlay"))
                        .append("pipeline", Arrays.asList(
                                new Document("$match",
                                        new Document("$expr",
                                                new Document("$and", Arrays.asList(
                                                        new Document("$eq", Arrays.asList("$uid", "$$gamePlay")),
                                                        new Document("$eq", Arrays.asList("$status", "INPROGRESS"))
                                                ))
                                        )
                                )
                        ))
                        .append("as", "gamePlayNode")
        );

        // Unwind stage for GamePlays
        UnwindOperation unwindGamePlay = unwind("gamePlayNode");

        // Sort stage for GamePlay
        SortOperation sortGamePlay = sort(Sort.by(Sort.Direction.DESC, "gamePlayNode._id"));

        // Limit stage for GamePlay
        AggregationOperation limitGamePlay = limit(1);

        // Lookup stage for GameActivities
        LookupOperation lookupGameActivity = LookupOperation.newLookup()
                .from("GameActivities")
                .localField("gamePlay")
                .foreignField("gamePlay")
                .as("gameActivityNode");

        // Unwind stage for GameActivities
        UnwindOperation unwindGameActivity = unwind("gameActivityNode");

        // Sort stage for GameActivity
        SortOperation sortGameActivity = sort(Sort.by(Sort.Direction.DESC, "gameActivityNode._id"));

        // Limit stage for GameActivity
        AggregationOperation limitGameActivity = limit(1);

        // Combine all the stages
        Aggregation aggregation = newAggregation(
                matchGameRound,
                lookupGamePlay,
                unwindGamePlay,
                sortGamePlay,
                limitGamePlay,
                lookupGameActivity,
                unwindGameActivity,
                sortGameActivity,
                limitGameActivity
        );

        // Execute the aggregation and return a Tuple3 of GameRound, GamePlay, and GameActivity
  /*      return getTemplate().aggregate(aggregation, "GameRounds", GameRoundDocument.class)
                .singleOrEmpty();*/

        AggregationResults<Document> documents = getTemplate().aggregate(aggregation, "GameRounds", Document.class);

        Document document;
        if(!documents.getMappedResults().isEmpty())
            document = documents.getMappedResults().getFirst();
        else
            return null;


        GameRound gameRound = getTemplate().getConverter().read(GameRound.class, document);
        UnfinishedGame unfinishedGame = new UnfinishedGame();
        unfinishedGame.setGameRound(gameRound);
        ObjectReader reader = objectMapper.readerFor(DBObject.class);

        try {
            DBObject gamePlayDBNode = BasicDBObject.parse(((Document) document.get("gamePlayNode")).toJson());
            DBObject gameActivityDBNode = BasicDBObject.parse(((Document) document.get("gameActivityNode")).toJson());


            JsonNode gamePlayJsonNode = objectMapper.readTree(objectMapper.writeValueAsString(gamePlayDBNode));
            JsonNode gameActivityJsonNode = objectMapper.readTree(objectMapper.writeValueAsString(gameActivityDBNode));

            unfinishedGame.setGamePlay(gamePlayJsonNode);
            unfinishedGame.setGameActivity(gameActivityJsonNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return unfinishedGame;
    }

    public Transaction updateTotalWinAndWallet(String gameRound, Transaction transaction) {
        if (transaction.getStatus() == Status.COMPLETED){
            addTransaction(gameRound, transaction);
        }
        return transaction;
    }
}