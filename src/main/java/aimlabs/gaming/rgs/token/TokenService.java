package aimlabs.gaming.rgs.token;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.users.User;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import aimlabs.gaming.rgs.core.utils.JwtUtil;
import aimlabs.gaming.rgs.core.entity.Status;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Slf4j
@Component
public class TokenService extends AbstractEntityService<Token, TokenDocument> {

        @Autowired
        JwtUtil jjwt;

        @Autowired
        TokenStore store;

        @Autowired
        TokenMapper mapper;

        public TokenResponse createToken(User user) {
                Token td = createRefreshToken(user);
                Token accessToken = createAccessTokenToken(td, user);
                return new TokenResponse(td.getRefreshToken(),
                                accessToken.getAccessToken(),
                                td.getExpiresAt(),
                                td.getCreatedOn());
        }

        public TokenResponse refreshToken(String refreshToken, User user) {
                Token td = findOneByRefreshTokenAndStatus(refreshToken);
                if (td == null) {
                        throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
                }

                if (td.getExpiresAt().before(new Date())) {
                        td.setStatus(Status.INACTIVE);
                        store.update(td.getId(), Map.of("status", Status.INACTIVE));
                        throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
                }

                Token accessToken = createAccessTokenToken(td, user);
                return new TokenResponse(td.getRefreshToken(),
                                accessToken.getAccessToken(),
                                accessToken.getExpiresAt(),
                                accessToken.getCreatedOn());
        }

        public Token findOneByRefreshTokenAndStatus(String refreshToken) {
                TokenDocument doc = store.findOneByRefreshTokenAndStatus(refreshToken, Status.ACTIVE);
                return doc != null ? getMapper().asDto(doc) : null;
        }

        private Token createAccessTokenToken(Token rt, User user) {
                Map<String, Object> claims;
                String uid = UUID.randomUUID().toString();
                claims = new HashMap<>(Map.of(
                                "sub", user.getTenant() + "|" + user.getId(),
                                "roles", user.getRoles() != null ? user.getRoles().stream()
                                                .reduce((role, role2) -> role + "|" + role2).orElse("") : "",
                                "jti", uid));
                /*
                 * log.info("Expiry Seconds: {}", jjwt.generateJws()
                 * .getExpireAccessTokenAfter()
                 * .toSeconds());
                 */
                String accessToken = jjwt.generateJws(new HashMap<>(), claims,
                                Duration.ofMinutes(15));

                Token token = new Token(uid, rt.getRefreshToken(), accessToken,
                                user.getTenant(), user.getId(),
                                Date.from(Instant.now()
                                                .plusSeconds(Duration.ofMinutes(15).toSeconds())));

                token.setAccessToken(accessToken);
                return this.create(token);
        }

        private Token createRefreshToken(User user) {
                Date expireAt = Date.from(Instant.now()
                                .plusSeconds(Duration.ofHours(24).toSeconds()));
                Token token = new Token(UUID.randomUUID().toString(),
                                user.getTenant(),
                                user.getId(),
                                user.getUsername(), expireAt);
                return create(token);
        }

        /*
         * public Mono<Token> findOne(String uid) {
         * return store.findOneByUid(uid);
         * }
         */
}
