package aimlabs.gaming.rgs.freespins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.promotions.PromotionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class FreeSpinsPromotionRequest {

    @JsonIgnore
    String clientId;

    @JsonIgnore
    String clientKey;

    String promotionRefId;

    String gameProvider;

    String brand;

    String player;

    List<String> games;

    // List<PlayerPromotion> playerPromotions;

    List<String> playerTags = new ArrayList<>();

    PromotionType promotionType;

    Date startDate;

    Date endDate;

    Integer freeSpins;

    Integer payLines;

    Map<String, Double> betAmounts;

    Status status;
}
