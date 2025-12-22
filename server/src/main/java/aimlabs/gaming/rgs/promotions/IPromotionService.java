package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.IEntityService;
import aimlabs.gaming.rgs.freespins.FreeSpinsPromotionRequest;

public interface IPromotionService extends IEntityService<Promotion> {

    Promotion findByPromotionRefId(String promotionRefId);
    Promotion award(FreeSpinsPromotionRequest fspReq);
    Promotion createIfNotFound(Promotion obj);
    Promotion closePromotionByPlayer(String promotion, String player);
}
