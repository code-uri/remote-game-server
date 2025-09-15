package aimlabs.gaming.rgs.playerbag;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import lombok.Data;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

@Data
@Service
public class PlayerBagStore extends MongoEntityStore<PlayerBagDocument> {

    public PlayerBagDocument findOneByPlayerCurrencyGameAndSession(GameSession gameSession, String gameSkin) {
        return getTemplate().findOne( query(where("player").is(gameSession.getPlayer())
                .and("currency").is(gameSession.getCurrency())
                .and("game").is(gameSkin).and("tenant").is(gameSession.getTenant())
                .and("deleted").is(false)
        ), PlayerBagDocument.class);
    }

    public PlayerBagDocument updateBag(String tenant,
                                       GameSession gameSession,
                                       GameSkin gameSkin,
                                       Map<String,Object> playerBag) {

        Update update = new Update().set("player", gameSession.getPlayer())
                .set("currency", gameSession.getCurrency())
                .set("game", gameSkin.getUid())
                .set("deleted", false);

        playerBag.forEach((key, value) -> update.set("data." + key, value));

        return   getTemplate()
                .findAndModify(
                        query(where("player").is(gameSession.getPlayer())
                                .and("currency").is(gameSession.getCurrency())
                                .and("game").is(gameSkin.getUid()).and("tenant").is(tenant)),
                        update,
                        FindAndModifyOptions.options().upsert(true).returnNew(true),
                        PlayerBagDocument.class);
    }
}