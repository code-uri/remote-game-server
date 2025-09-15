package aimlabs.gaming.rgs.promotions;

public interface IFreeSpinPromotionService {

    FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest freeSpinsPromotionRequest);
    FreeSpinsPromotionResponse cancelBonus(String promotionRefId);
    FreeSpinsPromotionResponse getPromotionByRefId(FreeSpinsPromotionRequest request);
}
