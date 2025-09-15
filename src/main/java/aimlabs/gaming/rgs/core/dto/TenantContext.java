package aimlabs.gaming.rgs.core.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class TenantContext implements Serializable {
    private String tenant;
    private String brand;
    private Map<String, Object> scope = new HashMap<>();
}
