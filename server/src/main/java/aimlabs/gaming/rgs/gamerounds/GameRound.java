package aimlabs.gaming.rgs.gamerounds;


import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.promotions.FreeSpinCampaign;
import aimlabs.gaming.rgs.promotions.PromoBonus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.money.MonetaryAmount;
import java.util.LinkedList;

@Data
public class GameRound extends BaseDto {

    private String uid;
    private String gamePlay;
    private String brand;
    private String gameId;
    private String gameConfiguration;
    private String gameType;
    private MonetaryAmount totalWager;
    private MonetaryAmount totalWin;
    private LinkedList<String> transactions = new LinkedList<>();
    private String rollbackTxnId;
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
}
