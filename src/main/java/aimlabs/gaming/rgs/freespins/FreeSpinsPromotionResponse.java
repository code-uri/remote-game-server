package aimlabs.gaming.rgs.freespins;

import aimlabs.gaming.rgs.core.entity.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FreeSpinsPromotionResponse {
        String promotionId;
        String promotionRefId;
        Status status;
}
