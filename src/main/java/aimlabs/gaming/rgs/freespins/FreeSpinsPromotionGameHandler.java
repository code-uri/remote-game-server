package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.games.GameFlowPipelineHandler;
import aimlabs.gaming.rgs.games.GameInitializer;
import aimlabs.gaming.rgs.games.GamePlayContext;
import aimlabs.gaming.rgs.games.GamePlayResponse;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.promotions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Order
@Slf4j
public class FreeSpinsPromotionGameHandler implements GameFlowPipelineHandler, GameInitializer {

    static final Duration LOCK_DURATION = Duration.ofSeconds(10);

    @Autowired
    PromotionStore promotionStore;

    @Autowired
    FreeSpinsAllotmentStore freeSpinsIssueStore;

    @Autowired
    FreeSpinsAllotmentService freeSpinsIssueService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    private GameFlowPipelineHandler nextHandler;

    private static PromoBonus createPromoBonus(FreeSpinsAllotment freeSpinsAllotment) {
        PromoBonus promoBonus = new PromoBonus();
        promoBonus.setBetAmount(freeSpinsAllotment.getBetAmount());
        promoBonus.setPayLines(freeSpinsAllotment.getPayLines());
        promoBonus.setFreeSpinsAwarded(freeSpinsAllotment.getFreeSpinsAwarded());
        promoBonus.setFreeSpinsRemaining(freeSpinsAllotment.getFreeSpinsRemaining());
        promoBonus.setPromotionType(PromotionType.FREE_GAMES);
        promoBonus.setFreeSpinsAllotmentId(freeSpinsAllotment.getId());
        promoBonus.setTotalWager(freeSpinsAllotment.getTotalWager());
        promoBonus.setTotalWin(freeSpinsAllotment.getTotalWin());
        promoBonus.setPromotionId(freeSpinsAllotment.getPromotionId());
        return promoBonus;
    }

    public JsonNode loadDate(ObjectNode promotionResponse,
            GameSkin gameSkin,
            GameSession gameSession,
            Map<String, Object> settings) {
        log.info("load permissions");
        FreeSpinsAllotment freeSpinsAllotment = freeSpinsIssueStore.findOneByGameAndPlayer(gameSkin.getUid(),
                gameSession.getPlayer());
        PromoBonus promoBonus = null;
        if (freeSpinsAllotment != null)
            promoBonus = createPromoBonus(freeSpinsAllotment);
        else {
            Promotion promotion = promotionStore.findPromotionsByGameAndPlayer(gameSession, gameSkin.getUid());

            freeSpinsAllotment = freeSpinsIssueStore.findOneByPromotionIdAndGameAndPlayer(promotion.getId(),
                    gameSkin.getUid(),
                    gameSession.getPlayer(),
                    List.of(Status.COMPLETED));

            if (freeSpinsAllotment == null) {
                promoBonus = createPromoBonusFromSettings(promotion, gameSession, settings);
            }
        }
        if (promoBonus != null)
            return updatePromotionResponse(promotionResponse, promoBonus);
        else
            return null;
    }

    private boolean isFreeSpinsIssueActive(FreeSpinsAllotmentDocument freeSpinsIssue) {
        return freeSpinsIssue.getStatus() == Status.ACTIVE || freeSpinsIssue.getStatus() == Status.INPROGRESS;
    }

    private PromoBonus createPromoBonus(FreeSpinsAllotmentDocument freeSpinsIssue) {
        PromoBonus promoBonus = new PromoBonus();
        promoBonus.setPromotionId(freeSpinsIssue.getPromotionId());
        promoBonus.setFreeSpinsAllotmentId(freeSpinsIssue.getId());
        promoBonus.setBetAmount(freeSpinsIssue.getBetAmount());
        promoBonus.setTotalWin(freeSpinsIssue.getTotalWin());
        promoBonus.setPayLines(freeSpinsIssue.getPayLines());
        promoBonus.setFreeSpinsAwarded(freeSpinsIssue.getFreeSpinsAwarded());
        promoBonus.setFreeSpinsRemaining(freeSpinsIssue.getFreeSpinsRemaining());
        return promoBonus;
    }

    private PromoBonus createPromoBonusFromSettings(Promotion promotion,
            GameSession gameSession,
            Map<String, Object> settings) {
        PromoBonus promoBonus = new PromoBonus();
        promoBonus.setPromotionId(promotion.getId());
        promoBonus.setBetAmount(promotion.getBetAmounts().get(gameSession.getCurrency()));
        promoBonus.setFreeSpinsAwarded(promotion.getFreeSpins());
        promoBonus.setFreeSpinsRemaining(promotion.getFreeSpins());

        Object minMaxLines = settings.get("minMaxLines");
        if (minMaxLines instanceof List<?> minMaxLinesList) {
            promoBonus.setPayLines(promotion.getPayLines() != null && promotion.getPayLines() > 0
                    ? promotion.getPayLines()
                    : (Integer) minMaxLinesList.get(minMaxLinesList.size() - 1));
        } else {
            promoBonus.setPayLines(promotion.getPayLines());
        }

        return promoBonus;
    }

    private JsonNode updatePromotionResponse(ObjectNode promotionResponse, PromoBonus promoBonus) {
        String key = promoBonus.getFreeSpinsAllotmentId() != null ? "continueFreeSpins" : "claimFreeSpins";
        return promotionResponse.set("promotionData",
                objectMapper.createObjectNode().set(key, objectMapper.valueToTree(promoBonus)));
    }

    private void processFreeSpinsAllotment(GamePlayResponse gamePlayResponse, String freeSpinsAllotmentId,
            GameSession gameSession,
            GamePlayContext ctx) {
        FreeSpinsAllotment freeSpinsAllotment = freeSpinsIssueStore.findOneByIdAndPlayerAndGame(freeSpinsAllotmentId,
                gameSession.getPlayer(),
                gameSession.getGame());

        validateAndLockFreeSpins(gamePlayResponse, gameSession.getPlayer(), gameSession.getGame(), freeSpinsAllotment,
                ctx);

        FreeSpinCampaign freeSpins = new FreeSpinCampaign();
        freeSpins.setCampaignUid(freeSpinsAllotment.getPromotionExternalRefId());
        freeSpins.setFreeSpinsAwarded(freeSpinsAllotment.getFreeSpinsAwarded());
        freeSpins.setFreeSpinsRemaining(freeSpinsAllotment.getFreeSpinsRemaining() - 1);
        freeSpins.setFreeSpinsPlayed(freeSpins.getFreeSpinsAwarded() - freeSpins.getFreeSpinsRemaining());
        gamePlayResponse.setFreeSpins(freeSpins);
        gamePlayResponse.setFreeSpinsAllotmentId(freeSpinsAllotment.getId());
        gamePlayResponse.setTotalWager(0);
        ctx.getEngineResponse().setTotalWager(0);

        if (this.nextHandler != null)
            this.nextHandler.handle(ctx.getGamePlayRequest(), ctx);

        consumeFreeSpin(gamePlayResponse,
                freeSpinsAllotment.getId(),
                gameSession.getPlayer(),
                gameSession.getGame());
    }

    private void validateAndLockFreeSpins(GamePlayResponse gamePlayResponse,
            String player,
            String gameId,
            FreeSpinsAllotment freeSpinsIssue,
            GamePlayContext ctx) {
        if (!gamePlayResponse.isContinueRound() && !isValidStake(gamePlayResponse, freeSpinsIssue, ctx)) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_STAKE);
        }

        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(player + "-" + gameId, true, LOCK_DURATION);

        if (Boolean.FALSE.equals(locked))
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST);

    }

    private boolean isValidStake(GamePlayResponse gamePlayResponse, FreeSpinsAllotment freeSpinsIssue,
            GamePlayContext ctx) {
        double totalWager = gamePlayResponse.getTotalWager();
        double betAmount = freeSpinsIssue.getBetAmount();

        if (ctx.getGamePlayRequest().get("noOfLines") != null) {
            int payLines = freeSpinsIssue.getPayLines() != null ? freeSpinsIssue.getPayLines()
                    : (ctx.getGamePlayRequest()).get("noOfLines").asInt();
            log.info("payLines " + payLines + " betamount " + betAmount + "totalWager " + totalWager);
            return (freeSpinsIssue.getStatus() == Status.ACTIVE || freeSpinsIssue.getStatus() == Status.INPROGRESS) &&
                    (payLines > 0 ? (betAmount * payLines) == totalWager : betAmount == totalWager);
        } else
            return betAmount == totalWager;
    }

    private void consumeFreeSpin(GamePlayResponse gamePlayResponse,
            String freeSpinsIssueId,
            String player,
            String gameId) {
        FreeSpinsAllotment freeSpinsAllotment = freeSpinsIssueService.consumeFreeSpin(freeSpinsIssueId,
                gamePlayResponse.getTotalWager(),
                gamePlayResponse.getTotalWinnings(),
                gamePlayResponse.isContinueRound());

        Object as = redisTemplate.opsForValue().getAndDelete(player + "-" + gameId);

        if (as != null) {
            // TODO fix this
            // gamePlayResponse.setFreeSpinsAllotment(freeSpinsAllotment);
            gamePlayResponse.getGameRound()
                    .setPromoBonus(createPromoBonus(freeSpinsAllotment));
            log.info("Removed lock on freespin issue id {} and game {}",
                    freeSpinsAllotment.getId(), gameId);
        }
    }

    @Override
    public void handle(JsonNode request, GamePlayContext ctx) {
        GamePlayResponse gamePlayResponse = ctx.getEngineResponse();
        GameSession gameSession = ctx.getGameSession();
        GameRound gameRound = ctx.getEngineResponse().getGameRound();
        log.info("{} ", ctx.getGamePlayRequest().has("freeSpinsAllotmentId"));
        if (ctx.getGamePlayRequest().has("freeSpinsAllotmentId")
                || (gameRound != null && gameRound.getFreeSpinsAllotmentId() != null)) {

            String freeSpinsAllotmentId;
            if (ctx.getGamePlayRequest().get("freeSpinsAllotmentId") != null)
                freeSpinsAllotmentId = ctx.getGamePlayRequest().get("freeSpinsAllotmentId").asText();
            else
                freeSpinsAllotmentId = ctx.getEngineResponse().getGameRound().getFreeSpinsAllotmentId();
            log.info("{}", freeSpinsAllotmentId);

            processFreeSpinsAllotment(gamePlayResponse, freeSpinsAllotmentId, gameSession, ctx);
        } else {
            if (this.nextHandler != null)
                this.nextHandler.handle(request, ctx);
        }
    }

    @Override
    public void setNext(GameFlowPipelineHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    @Override
    public JsonNode loadData(GameSession gameSession, GameSkin gameSkin, Map<String, Object> settings) {
        ObjectNode promotionResponse = objectMapper.createObjectNode();

        if (gameSession.isDemo())
            return null;

        return loadDate(promotionResponse, gameSkin, gameSession, settings);
    }
}