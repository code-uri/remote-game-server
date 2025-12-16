package aimlabs.gaming.rgs.tenant;

import lombok.Data;

import java.lang.ScopedValue;

@Data
public class TenantContextHolder {

    private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    public static String getTenant() {
        if (TENANT.isBound())
            return TENANT.get();
        return "default";
    }

    public static ScopedValue<String> getScopedValue() {
        return TENANT;
    }
}