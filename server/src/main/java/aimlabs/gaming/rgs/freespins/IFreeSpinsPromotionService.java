package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionRequest;
import aimlabs.gaming.rgs.promotions.FreeSpinsPromotionResponse;

public interface IFreeSpinsPromotionService {

    FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest request);

    FreeSpinsPromotionResponse cancelBonus(FreeSpinsPromotionRequest request);

}
