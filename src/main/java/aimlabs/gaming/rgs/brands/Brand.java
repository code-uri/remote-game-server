package aimlabs.gaming.rgs.brands;


import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Brand extends BaseDto {

    private String uid;
    private String network;
    private String name;
    private String currency;
    private BigDecimal demoBalance;
    private String realmType = "LOCAL";
    private String realm;
    private String parent;
    private boolean defaultConnector;
    private String connectorUid;
    private Map<String, String> urls = new HashMap<>();
    public BigDecimal getDemoBalance() {
        return demoBalance != null ? demoBalance : BigDecimal.valueOf(100);
    }
    private String jurisdiction;
    private long realityCheckIntervalInMilliSeconds;

}
