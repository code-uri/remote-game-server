package aimlabs.gaming.rgs.core.utils;


import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Data
public class JwtUtil {

    private  String clientId;
    private  String clientSecret;

    public JwtUtil(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String generateJws(Map<String, Object> headerParams, Map<String, Object> claims, Duration expire) {
        return Jwts.builder()
                .claims(claims)
                .header().add(headerParams)
                .and()
                .issuer(clientId)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusMillis(expire.toMillis())))
                .signWith(Keys.hmacShaKeyFor(clientSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public Claims decodeJWT(String jwt) {
        jwt = jwt.replace("Bearer ", "");
        //This line will throw an exception if it is not a signed JWS (as expected)

        return Jwts.parser()
                .decryptWith(Keys.hmacShaKeyFor(clientSecret.getBytes(StandardCharsets.UTF_8))) // Updated method
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }


    public <T> T getClaim(String token, String claimName, Class<T> requiredType) {
        return decodeJWT(token).get(claimName, requiredType);
    }

    public String getIdentityFromToken(String token) {
        if (token == null || token.trim().equalsIgnoreCase("")) return null;
        Claims claims = decodeJWT(token);
        String sub = claims.getSubject();
        String[] roles = Optional.of(claims.get("roles")).orElse("").toString().split("\\|");
        String[] parts = sub.split("\\|");
        String tenant = parts[0];
        String account = parts[1];
        String identity = parts[2];
        return identity;
    }
}
