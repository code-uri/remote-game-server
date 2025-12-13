package aimlabs.gaming.rgs.token;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document("Tokens")
@Data
@NoArgsConstructor
public class TokenDocument extends EntityDocument {

    private String uid;
    private String refreshToken;
    private String identity;
    private Date expiresAt;
    private Object tokenType;
    private String identifier;
    private String accessToken;

    /*
     * public TokenDocument(String refreshToken, String tenant, String identity,
     * String identifier, Date expireRefreshTokenAfter) {
     * this.uid = UUID.randomUUID().toString();
     * this.refreshToken= refreshToken;
     * this.tenant = tenant;
     * this.tokenType = TokenType.REFRESH_TOKEN;
     * this.identity = identity;
     * this.identifier = identifier;
     * this.expiresAt = expireRefreshTokenAfter;
     * this.status = Status.ACTIVE;
     * }
     * 
     * public TokenDocument(String uid, String refreshToken, String accessToken,
     * String tenant, String identity, Date expiresAt) {
     * this.uid = uid;
     * this.refreshToken = refreshToken;
     * this.accessToken = accessToken;
     * this.tokenType = TokenType.ACCESS_TOKEN;
     * this.tenant = tenant;
     * this.identity = identity;
     * this.expiresAt = expiresAt;
     * }
     */

}
