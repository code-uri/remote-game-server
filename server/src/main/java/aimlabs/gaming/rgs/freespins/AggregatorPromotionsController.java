package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.UpdatePlayerTagsRequest;
import aimlabs.gaming.rgs.networks.INetworkService;
import aimlabs.gaming.rgs.networks.Network;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.promotions.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
@Data
@RestController
@RequestMapping("/connect/promotions")
public class AggregatorPromotionsController {

    @Autowired
    INetworkService networkService;

    @Autowired
    IPromotionService promotionService;

    @Autowired
    IPlayerService playerService;

    @Autowired
    IFreeSpinsPromotionService freeSpinsPromotionService;

    @PostMapping("/award-bonus")
    FreeSpinsPromotionResponse createFreeSpinsPromotion(@RequestBody FreeSpinsPromotionRequest request,
                                                        @RequestHeader("X-Client-ID") String clientId,
                                                        @RequestHeader("X-Client-Key") String clientKey){
        request.setClientId(clientId);
        request.setClientKey(clientKey);
        return freeSpinsPromotionService.awardBonus(request);

    }


    @GetMapping("/{promotionRefId}")
    FreeSpinsPromotionResponse getFreeSpinsPromotion(@PathVariable("promotionRefId") String promotionRefId,
                                                          @RequestHeader("X-Client-ID") String clientId,
                                                          @RequestHeader("X-Client-Key") String clientKey){

        Network network = networkService.findOneByClientId(clientId);
        if(network==null){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_CLIENT_ID, "Invalid Client ID");
        }
        Promotion promotion = promotionService.findByPromotionRefId(promotionRefId);
        if(promotion==null){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_PROMOTION_REFERENCE, "Promotion not found");
        }
        return new FreeSpinsPromotionResponse(promotion.getId(), promotion.getPromotionRefId(), promotion.getStatus());
    }

    @GetMapping ("/cancel-bonus/{promotionRefId}")
    FreeSpinsPromotionResponse cancelPromotionByRefId(@PathVariable String promotionRefId,
                                      @RequestHeader("X-Client-ID") String clientId,
                                      @RequestHeader("X-Client-Key") String clientKey){
        Network network = networkService.findOneByClientId(clientId);
        if(network==null){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_CLIENT_ID, "Invalid Client ID");
        }

        FreeSpinsPromotionRequest request = new FreeSpinsPromotionRequest();
        request.setClientId(clientId);
        request.setClientKey(clientKey);
        request.setPromotionRefId(promotionRefId);
        return freeSpinsPromotionService.cancelBonus(request);
    }

    @PostMapping("/update-player-tags")
    void updatePlayerTags(@RequestBody UpdatePlayerTagsRequest request,
                                @RequestHeader("X-Client-ID") String clientId,
                                @RequestHeader("X-Client-Key") String clientSecret){
        Network network = networkService.findOneByClientId(clientId);
        if(network==null){
            throw new BaseRuntimeException(SystemErrorCode.INVALID_CLIENT_ID, "Invalid Client ID");
        }

        Player player = playerService.findAndUpdatePlayerTagsByCorrelationIdAndNetworkAndBrand(network.getUid(), request.getBrand(), request.getPlayer(), request.getTags());

    }
}