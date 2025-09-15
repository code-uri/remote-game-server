package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Data
@Slf4j
@Service
public
class FreeSpinsAllotmentStore extends MongoEntityStore<FreeSpinsAllotmentDocument> {

    @Autowired
    FreeSpinsAllotmentMapper mapper;

    public FreeSpinsAllotment findOneByPromotionIdAndGameAndPlayer(String promotionId,
                                                                                 String game,
                                                                                 String player, List<Status> statuses) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("promotionId").is(promotionId)
                        .and("game").is(game)
                        .and("player").is(player)
                        .and("deleted").is(false)
                        .and("status").in(statuses)
                ),
                FreeSpinsAllotment.class, "FreeSpinsAllotments");
    }

    public FreeSpinsAllotment findOneByIdAndPlayerAndGame(String id, String player, String game) {

        return getTemplate().findOne(Query.query(Criteria.where("id").is(id)
                        .and("game").is(game)
                        .and("player").is(player)
                        .and("freeSpinsRemaining").gte(1)
                        .and("status").in(List.of(Status.ACTIVE, Status.INPROGRESS))
                        .and("deleted").is(false)
                ),
                FreeSpinsAllotment.class,"FreeSpinsAllotments");
    }

    public FreeSpinsAllotmentDocument rollbackFreeSpinClaim(String issueId, String player, String game) {
        return getTemplate().findAndModify(Query.query(Criteria.where("id").is(issueId)
                        .and("game").is(game)
                        .and("player").is(player).and("status").is(Status.INPROGRESS)
                        .and("freeSpinsRemaining").gte(1).and("deleted").is(false)),
                Update.update("modifiedOn", new Date())
                        //.set("status", Status.INPROGRESS)
                        .inc("freeSpinsRemaining", 1),
                FindAndModifyOptions.options().returnNew(true),
                FreeSpinsAllotmentDocument.class);
    }

    public FreeSpinsAllotment consumeFreeSpin(String freeSpinsIssueId,
                                                            double totalWager,
                                                            double totalWin,
                                                            boolean continueRound) {
        FreeSpinsAllotment freeSpinsIssueDocument = getTemplate()
                .findAndModify(Query.query(Criteria.where("id")
                                .is(freeSpinsIssueId)),
                        Update.update("modifiedOn", new Date())
                                .inc("freeSpinsRemaining", continueRound ? 0 : -1)
                                .inc("totalWager", totalWager)
                                .inc("totalWin", totalWin)
                        ,
                        FindAndModifyOptions.options().returnNew(true),
                        FreeSpinsAllotment.class, "FreeSpinsAllotments");

        assert freeSpinsIssueDocument != null;
        if (freeSpinsIssueDocument.getStatus() == Status.INPROGRESS
            && freeSpinsIssueDocument.getFreeSpinsRemaining() == 0) {
            freeSpinsIssueDocument.setStatus(Status.COMPLETED);

            freeSpinsIssueDocument = getTemplate()
                    .findAndModify(Query.query(Criteria.where("id").is(freeSpinsIssueDocument.getId())),
                            Update.update("status", Status.COMPLETED).set("modifiedOn", new Date()),
                            FreeSpinsAllotment.class, "FreeSpinsAllotments");
        }

        return freeSpinsIssueDocument;
    }

    public FreeSpinsAllotment findOneByGameAndPlayer(String game, String player) {
        return getTemplate().findOne(Query.query(Criteria.where("tenant").is(TenantContextHolder.getTenant())
                        .and("game").is(game)
                        .and("player").is(player)
                        .and("freeSpinsRemaining").gte(1)
                        .and("status").in(List.of(Status.ACTIVE, Status.INPROGRESS))
                        .and("deleted").is(false)
                ),
                FreeSpinsAllotment.class, "FreeSpinsAllotments");

    }
}