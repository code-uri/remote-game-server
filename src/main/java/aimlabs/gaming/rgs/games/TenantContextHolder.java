package aimlabs.gaming.rgs.games;

import lombok.Data;

import java.lang.ScopedValue;


@Data
public class TenantContextHolder {

    private static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    public static String getTenant() {
        return TENANT.get();
    }
}