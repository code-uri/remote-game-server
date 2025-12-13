package aimlabs.gaming.rgs.token;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class TokenResponse {

    String refreshToken;
    String accessToken;
    Date expiresAt;
    Date createdOn;
}
