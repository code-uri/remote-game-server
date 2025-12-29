package aimlabs.gaming.rgs.promotions;


public interface IGameSupplierPromotionsService {

    FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest request);
    FreeSpinsPromotionResponse getPromotionByRefId(FreeSpinsPromotionRequest request);
    FreeSpinsPromotionResponse cancelBonus(String promotionRefId);

}
