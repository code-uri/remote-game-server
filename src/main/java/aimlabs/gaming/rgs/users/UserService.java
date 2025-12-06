package aimlabs.gaming.rgs.users;

import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.token.TokenService;
import aimlabs.gaming.rgs.token.Token;
import aimlabs.gaming.rgs.token.TokenResponse;
import aimlabs.gaming.rgs.token.AuthResponse;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aimlabs.gaming.rgs.core.AbstractEntityService;

import java.util.function.Function;

@Data
@Service
public class UserService extends AbstractEntityService<User, UserDocument> {

    @Autowired
    private UserStore store;

    @Autowired
    private UserMapper mapper;

    @Autowired
    UserCredentialService userCredentialService;

    @Autowired
    TokenService tokenService;

    /*
     * @Autowired
     * JWTClientProperties jjwt;
     */

    @Override
    public User create(User user) {
        User existingUser = findOneByUsername(user.getTenant(), user.getUsername());
        if (existingUser != null) {
            throw new BaseRuntimeException(SystemErrorCode.USER_ALREADY_REGISTERED);
        }

        User savedUser = super.create(user);
        UserCredential userCred = new UserCredential(savedUser.getId(),
                savedUser.getTenant(),
                savedUser.getUsername(),
                user.getPassword());
        userCredentialService.create(userCred);
        return savedUser;
    }

    private User findOneByUsername(String tenant, String username) {
        UserDocument doc = getStore().findOneByUsername(tenant, username);
        return doc != null ? getMapper().asDto(doc) : null;
    }

    public AuthResponse login(UserCredential userCredential) {
        String userId = userCredentialService.login(userCredential);
        User user = findOne(userId);
        TokenResponse tokenResponse = tokenService.createToken(user);
        return getAuthResponse().apply(tokenResponse);
    }

    public AuthResponse refreshToken(String refreshToken) {
        Token token = tokenService.findOneByRefreshTokenAndStatus(refreshToken);
        if (token == null) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
        }

        UserDocument userDocument = store.findOneByUsername(token.getTenant(), token.getIdentifier());
        if (userDocument == null) {
            throw new BaseRuntimeException(SystemErrorCode.USER_NOT_FOUND);
        }

        User user = mapper.asDto(userDocument);
        TokenResponse tokenResponse = tokenService.refreshToken(token.getRefreshToken(), user);
        return getAuthResponse().apply(tokenResponse);
    }

    private Function<TokenResponse, AuthResponse> getAuthResponse() {
        return tr -> new AuthResponse(tr.getRefreshToken(),
                tr.getAccessToken(),
                tr.getExpiresAt(),
                tr.getCreatedOn());
    }
}
