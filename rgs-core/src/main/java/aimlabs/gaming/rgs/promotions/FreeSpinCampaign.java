package aimlabs.gaming.rgs.promotions;

import lombok.Data;

/**
 * FreeSpinCampaign
 */
@Data
public class FreeSpinCampaign {
    private String campaignUid;
    private int freeSpinsAwarded;
    private int freeSpinsPlayed;
    private int freeSpinsRemaining;
}
