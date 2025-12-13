package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.token.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import aimlabs.gaming.rgs.token.Token;
import aimlabs.gaming.rgs.users.User;
import aimlabs.gaming.rgs.users.UserCredential;
import aimlabs.gaming.rgs.users.UserCredentialService;
import aimlabs.gaming.rgs.users.UserService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/users")
public class UserController extends AbstractEntityCurdController<User> {

    @Autowired
    private UserService service;

    @Autowired
    private UserCredentialService credentialService;

    @PostMapping({ "login" })
    public AuthResponse login(@RequestBody UserCredential userCredential, HttpServletRequest request) {
        String tenant = (String) request.getAttribute("TENANT");
        if (tenant == null) {
            tenant = "default";
        }
        userCredential.setTenant(tenant);
        return getService().login(userCredential);
    }

    @PostMapping({ "/refresh" })
    public AuthResponse refreshToken(@RequestBody Token refreshToken) {
        return getService().refreshToken(refreshToken.getRefreshToken());
    }

}
