package aimlabs.gaming.rgs.token;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@AllArgsConstructor
public class AuthResponse implements Serializable {

    String refreshToken;
    String accessToken;
    Date expiresIn;
    Date createdOn;
}
