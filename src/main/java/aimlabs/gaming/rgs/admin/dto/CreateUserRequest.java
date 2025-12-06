package aimlabs.gaming.rgs.admin.dto;

import aimlabs.gaming.rgs.users.User;
import lombok.Data;

@Data
public class CreateUserRequest {

    User user;
    String password;
}
