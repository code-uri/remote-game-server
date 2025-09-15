package aimlabs.gaming.rgs.promotions;

import lombok.Data;

@Data
public class PromoBonus {


    Double cash;

    String currency;

    Integer freeSpinsAwarded;

    Integer freeSpinsRemaining;

    String betLevel;

    Integer payLines;

    Double betAmount;

    String bonusFeature;

    PromotionType promotionType;

    String freeSpinsAllotmentId;

    String promotionId;

    Double totalWager;

    Double totalWin;

    boolean streak;
}


