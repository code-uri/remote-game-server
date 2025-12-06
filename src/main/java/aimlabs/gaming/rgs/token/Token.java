package aimlabs.gaming.rgs.token;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.entity.Status;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
public class Token extends BaseDto {

    private String uid;
    private String refreshToken;
    private String identity;
    private Date expiresAt;
    private Object tokenType;
    private String identifier;
    private String accessToken;

    public Token(String refreshToken, String tenant, String identity, String identifier, Date expireRefreshTokenAfter) {
        this.uid = UUID.randomUUID().toString();
        this.refreshToken = refreshToken;
        this.tenant = tenant;
        this.tokenType = TokenType.REFRESH_TOKEN;
        this.identity = identity;
        this.identifier = identifier;
        this.expiresAt = expireRefreshTokenAfter;
        this.status = Status.ACTIVE;
    }

    public Token(String uid, String refreshToken, String accessToken, String tenant, String identity, Date expiresAt) {
        this.uid = uid;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.tokenType = TokenType.ACCESS_TOKEN;
        this.tenant = tenant;
        this.identity = identity;
        this.expiresAt = expiresAt;
        this.status = Status.ACTIVE;
    }

}
