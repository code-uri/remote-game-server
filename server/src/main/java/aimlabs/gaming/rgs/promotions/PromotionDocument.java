package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "Promotions")
@CompoundIndexes({
        @CompoundIndex(name = "tenant_1_network_1_promotionRefId_1_player_1", def = "{'tenant': 1,'network': 1, 'promotionRefId': 1, 'player':1}", background = true)
})
public class PromotionDocument extends EntityDocument {

    String network;

    String brand;

    String promotionRefId;

    List<String> games;

    List<String> playerTags;

    String player;

    PromotionType promotionType;

    Date startDate;

    Date endDate;

    //Bonus bonus;

    //FreeSpins freeSpins;

    Integer freeSpins;

    Map<String, Double> betAmounts;

    Integer payLines;

    // Map<String, Double> betLevels;
}
