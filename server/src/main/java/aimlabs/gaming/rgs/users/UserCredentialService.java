package aimlabs.gaming.rgs.users;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.users.UserCredentialStore;
import aimlabs.gaming.rgs.core.exceptions.BaseException;
import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.security.AdminUserAuthenticationToken;
import aimlabs.gaming.rgs.users.UserCredentialMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Data
@Service
@Slf4j
public class UserCredentialService extends AbstractEntityService<UserCredential, UserCredentialDocument>
        implements ApplicationListener<AuthenticationSuccessEvent> {

    @Autowired
    UserCredentialStore store;

    @Autowired
    UserCredentialMapper mapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    public String login(UserCredential userCredential) {
        UserCredentialDocument ucd = store.findOneByUserName(userCredential.getTenant(), userCredential.getUsername());
        if (ucd == null || !passwordEncoder.matches(userCredential.getPassword(), ucd.getPassword())) {
            throw new BaseRuntimeException(SystemErrorCode.USER_NOT_FOUND);
        }
        log.info(" Identity {} ", ucd.getIdentity());
        return ucd.getIdentity();
    }

    @Override
    public UserCredential create(UserCredential userCredential) {
        userCredential.setPassword(passwordEncoder.encode(userCredential.getPassword()));
        UserCredentialDocument existing = store.findOneByUserName(userCredential.getTenant(),
                userCredential.getUsername());
        if (existing != null) {
            throw new BaseRuntimeException(SystemErrorCode.USER_ALREADY_REGISTERED);
        }
        return super.create(userCredential);
    }

    public Boolean isUserCredentialsExists(String tenant, String username) {
        UserCredentialDocument doc = store.findOneByUserName(tenant, username);
        return doc != null;
    }

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {

        Authentication auth = event.getAuthentication();

        if (auth instanceof AdminUserAuthenticationToken
                && auth.getCredentials() != null) {

            CharSequence clearTextPass = (CharSequence) auth.getCredentials();
            String newPasswordHash = passwordEncoder.encode(clearTextPass);

            log.info("Upgrade to new password {}", newPasswordHash);
            // [...] Update user's password

        }

    }
}