package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class Promotion extends BaseDto {

    String network;

    String brand;

    String promotionRefId;

    List<String> games;

    List<String> playerTags;

    String player;

    PromotionType promotionType;

    Date startDate;

    Date endDate;

    // Bonus bonus;

    // FreeSpins freeSpins;

    Integer freeSpins;

    Map<String, Double> betAmounts;

    Integer payLines;

    // Map<String, Double> betLevels;
}
