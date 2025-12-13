package aimlabs.gaming.rgs.security;

import lombok.Data;

import java.util.List;

@Data
public class UserDetails {

    String id;
    String tenant;
    List<String> roles;

    public UserDetails(String tenant, String identity, List<String> roles) {
        this.tenant = tenant;
        this.id = identity;
        this.roles = roles;
    }

    public String getTenant() {
        return (tenant!=null?tenant.toLowerCase():null);
    }
}
