package aimlabs.gaming.rgs.promotions;


public interface IFreeSpinsPromotionService {

    FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest request);

    FreeSpinsPromotionResponse cancelBonus(FreeSpinsPromotionRequest request);

}
