package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;

@Data
@Slf4j
@Service
public
class PromotionStore extends MongoEntityStore<PromotionDocument> {

    @Autowired
    PromotionMapper mapper;

    public Promotion findPromotionsByGameAndPlayer(GameSession gameSession, String game) {

        return  getTemplate()
                .findOne(Query.query(Criteria.where("deleted").is(false)
                                        .and("tenant").is(gameSession.getTenant())
                                       // .and("network").is(gameSession.getNetwork())
                                        .and("games").is(game)
                                        .and("brand").is(gameSession.getBrand())
                                        .and("player").in(gameSession.getPlayer())
                                        .and("status").is(Status.ACTIVE)
                                        //.and("streak").is(true)
                                        .and("endDate").gte(new Date())
                                //.and("streakConfigs").exists(true)
                        ).with(Sort.by(Sort.Direction.DESC, "player")
                                .and(Sort.by(Sort.Direction.ASC, "id"))),
                        Promotion.class, "Promotions");

    }
}