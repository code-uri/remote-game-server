package aimlabs.gaming.rgs.promotions;

import aimlabs.gaming.rgs.core.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FreeSpinsPromotionResponse {
        String promotionId;
        String promotionRefId;
        Status status;
}
