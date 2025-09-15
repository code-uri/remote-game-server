package aimlabs.gaming.rgs.gamerounds;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.players.PlayerWallet;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.promotions.PromoBonus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.money.MonetaryAmount;
import java.util.List;

@Data
@Document(collection = "GameRounds")
@CompoundIndexes({
        @CompoundIndex(name = "player_1_gameId_1_demo_1_status_-1_modifiedOn_-1_gamePlay_1", def = "{'player': 1, 'gameId': 1, 'demo':1, 'status': -1,'modifiedOn': -1, 'gamePlay': 1}", background = true),
        @CompoundIndex(name = "modifiedOn_-1_status_1", def = "{'modifiedOn': -1, 'status': 1}", background = true),
        @CompoundIndex(name = "tenant_1_game_1_correlationId_1", def = "{'tenant': 1, 'game': 1, 'correlationId': 1}", background = true),
        @CompoundIndex(def = "{ tenant: 1, deleted: 1, createdOn: -1 }", collation = "en", background = true)
})
public class GameRoundDocument extends EntityDocument {

    @Indexed(name = "uid_1", unique = true)
    private String uid;

    private String gamePlay;

    private String brand;

    private String gameId;

    private String gameConfiguration;

    private String gameType;

    private MonetaryAmount totalWager;

    private MonetaryAmount totalWin;

    /*private BigDecimal streakWins;

    private int streak;*/

    private List<String> transactions;

    private boolean demo;

    private String player;
    @JsonIgnore
    private String playerCorrelationId;

    private String session;

    private boolean autoPlayed;

    private boolean autoPlayable;

    private boolean handConfirmed;

    private PromoBonus promoBonus;

    private FreeSpinCampaign freeSpins;

    private String freeSpinsAllotmentId;

    private String promotionRefId;

    private String provider;

    //private PlayerWallet wallet;
}
