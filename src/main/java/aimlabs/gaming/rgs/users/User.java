package aimlabs.gaming.rgs.users;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import aimlabs.gaming.rgs.core.entity.BaseDto;

@Data
@NoArgsConstructor
public class User extends BaseDto {

    String username;

    String password;

    String firstName;

    String lastName;

    String email;

    List<String> roles;

    public User(String tenant, String identity, List<String> roles) {
        this.id = identity;
        this.tenant = tenant;
        this.roles = roles;
    }

    public String getTenant() {
        return super.getTenant() != null ? super.getTenant().toLowerCase() : null;
    }
}
