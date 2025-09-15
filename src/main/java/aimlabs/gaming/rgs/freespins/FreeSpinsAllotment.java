package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;

import java.util.Date;

@Data
public class FreeSpinsAllotment extends BaseDto {

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