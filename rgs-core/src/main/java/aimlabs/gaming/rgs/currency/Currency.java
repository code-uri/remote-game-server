package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class Currency extends BaseDto {
    private String code;
    private int numericCode;
    private String name;
    private String type;
    private boolean iso = false;
    private String description;
    private int fractionalDigits;
}
