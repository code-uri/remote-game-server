package aimlabs.gaming.rgs.gamesupplier;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.freespins.FreeSpinsAllotment;
import aimlabs.gaming.rgs.freespins.FreeSpinsAllotmentService;
import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionRequest;
import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionResponse;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.promotions.Promotion;
import aimlabs.gaming.rgs.promotions.PromotionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/games")
@Slf4j
public class GameProviderPromotionsController {

    @Autowired
    PromotionService promotionService;

    @Autowired
    FreeSpinsAllotmentService freeSpinsIssueService;

    @PostMapping("/promotions/award")
    public FreeSpinsPromotionResponse awardPromotion(@RequestBody FreeSpinsPromotionRequest freeSpinsPromotionRequest,
            @RequestHeader("X-Client-ID") String clientId,
            @RequestHeader("X-Client-Key") String clientKey,
            HttpServletRequest httpServletRequest) {

        log.info("Awarding promotion for clientId: {}", clientId);
        freeSpinsPromotionRequest.setClientId(clientId);
        freeSpinsPromotionRequest.setClientKey(clientKey);
        Promotion promotion = promotionService.award(freeSpinsPromotionRequest);

        return new FreeSpinsPromotionResponse(promotion.getId(), promotion.getPromotionRefId(), promotion.getStatus());
    }

    @GetMapping("/promotions/{promotionRefId}")
    public FreeSpinsPromotionResponse getPromotion(@PathVariable String promotionRefId,
            HttpServletRequest httpServletRequest) {
        Promotion promotion = promotionService.findByPromotionRefId(promotionRefId);
        if (promotion == null) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Promotion not found");
        }
        return new FreeSpinsPromotionResponse(promotion.getId(), promotion.getPromotionRefId(), promotion.getStatus());
    }

    @DeleteMapping("/promotions/{promotionRefId}")
    public FreeSpinsPromotionResponse cancelPromotion(@PathVariable String promotionRefId,
            HttpServletRequest httpServletRequest) {

        Promotion promotion = promotionService.findByPromotionRefId(promotionRefId);
        if (promotion == null)
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Promotion not found");


        promotion = promotionService.updatePartial(promotion.getId(), Map.of("status", Status.CANCELLED));
        return new FreeSpinsPromotionResponse(promotion.getId(), promotion.getPromotionRefId(), promotion.getStatus());
    }

    @GetMapping("/promotions/{id}/claim")
    public FreeSpinsAllotment claimPromotion(@PathVariable(name = "id") String promotionId,
            GameSession gameSession,
            HttpServletRequest httpServletRequest) {
        // String ipaddress = getRemoteIPAddress(serverHttpRequest);

        return freeSpinsIssueService.claimFreeSpin(promotionId, gameSession);
    }

}
