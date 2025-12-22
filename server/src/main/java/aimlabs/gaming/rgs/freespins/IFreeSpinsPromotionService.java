package aimlabs.gaming.rgs.freespins;


public interface IFreeSpinsPromotionService {

    FreeSpinsPromotionResponse awardBonus(FreeSpinsPromotionRequest request);

    FreeSpinsPromotionResponse cancelBonus(FreeSpinsPromotionRequest request);

}
