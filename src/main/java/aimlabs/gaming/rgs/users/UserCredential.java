package aimlabs.gaming.rgs.users;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserCredential extends BaseDto {

    String identity;

    String username;

    String password;

    public UserCredential(String identity, String tenant, String username, String password) {
        this.identity = identity;
        this.tenant = tenant;
        this.username = username;
        this.password = password;
    }

    public String getTenant() {
        return super.getTenant().toLowerCase();
    }
}
