package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "FreeSpinsAllotments")
public class FreeSpinsAllotmentDocument extends EntityDocument {

    String promotionId;

    String promotionExternalRefId;

    String player;

    String game;

    String currency;

    Integer freeSpinsAwarded;

    Integer freeSpinsRemaining;

    Integer payLines;

    Double betAmount;

    Double totalWager;

    Double totalWin;

    String betLevel;

    Date expiryDate;
}
