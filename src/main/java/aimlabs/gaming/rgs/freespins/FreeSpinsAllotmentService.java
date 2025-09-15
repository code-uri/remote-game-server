package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.promotions.Promotion;
import aimlabs.gaming.rgs.promotions.PromotionService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Service
public class FreeSpinsAllotmentService extends AbstractEntityService<FreeSpinsAllotment, FreeSpinsAllotmentDocument> {

    @Autowired
    FreeSpinsAllotmentStore store;

    @Autowired
    FreeSpinsAllotmentMapper mapper;

    @Autowired
    PromotionService promotionService;

    public FreeSpinsAllotment claimFreeSpin(String promotionId, GameSession gameSession) {
        Promotion promotion = promotionService.findOne(promotionId);

        if (promotion.getEndDate().before(new Date())) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_FREE_SPINS_REQUEST, "Promotion Expired");
        }

        FreeSpinsAllotment freeSpinsAllotment = store.findOneByPromotionIdAndGameAndPlayer(promotionId,
                gameSession.getGame(),
                gameSession.getPlayer(),
                List.of(Status.ACTIVE, Status.INPROGRESS, Status.COMPLETED));


        if(freeSpinsAllotment==null){

            FreeSpinsAllotment freeSpinsIssue = new FreeSpinsAllotment();

            freeSpinsIssue.setGame(gameSession.getGame());
            freeSpinsIssue.setPromotionId(promotion.getId());
            freeSpinsIssue.setPromotionExternalRefId(promotion.getPromotionRefId());
            freeSpinsIssue.setBetAmount(promotion.getBetAmounts().get(gameSession.getCurrency()));
            freeSpinsIssue.setPayLines(promotion.getPayLines());
            freeSpinsIssue.setPlayer(gameSession.getPlayer());
            freeSpinsIssue.setCurrency(gameSession.getCurrency());
            freeSpinsIssue.setExpiryDate(promotion.getEndDate());
            freeSpinsIssue.setFreeSpinsAwarded(promotion.getFreeSpins());
            freeSpinsIssue.setFreeSpinsRemaining(promotion.getFreeSpins());
            freeSpinsIssue.setTenant(gameSession.getTenant());
            freeSpinsIssue.setStatus(Status.INPROGRESS);

            freeSpinsAllotment =  create(freeSpinsIssue);


            promotionService.updatePartial(freeSpinsAllotment.getPromotionId(), Map.of("status", Status.INPROGRESS.name(), "player",
                    gameSession.getPlayer()));
        }

        if (freeSpinsAllotment.getStatus() == Status.COMPLETED
            || freeSpinsAllotment.getExpiryDate().before(new Date()))
            throw new BaseRuntimeException(SystemErrorCode.INVALID_FREE_SPINS_REQUEST, "Promotion claimed or Expired");

        return freeSpinsAllotment;
    }

    public FreeSpinsAllotment consumeFreeSpin(String freeSpinsIssueId, double totalWager, double totalWinnings, boolean continueRound) {
        FreeSpinsAllotment freeSpinsIssue =  store.consumeFreeSpin(freeSpinsIssueId, totalWager, totalWinnings, continueRound);

        if(freeSpinsIssue.getStatus()==Status.COMPLETED){
            promotionService.closePromotionByPlayer(freeSpinsIssue.getPromotionId(),freeSpinsIssue.getPlayer());
        }
        return freeSpinsIssue;
    }
}
