package aimlabs.gaming.rgs.security;

import aimlabs.gaming.rgs.core.EntityController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
public class RestEndpointAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext>, BeanPostProcessor {

    public static final String ADMIN_API_PATH = "/admin/";

    @Autowired
    @Qualifier("securedResources")
    Set<String> securedResources;


    @Override
    public void verify(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        AuthorizationManager.super.verify(authentication, object);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        return (AuthorizationDecision) authorize(authentication, object);
    }

    @Override
    public AuthorizationResult authorize(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        String requestedResource = PermissionsUtils.getPermission(object);
        AuthorizationDecision authorizationDecision = superAdminAuthzDecision(authentication.get());

        if (!authorizationDecision.isGranted()) {
            return authentication.get()
                    .getAuthorities()
                    .stream().map(simpleGrantedAuthority -> {
                        boolean matched = false;

                        String permission = null;
                        if (simpleGrantedAuthority.getAuthority().startsWith("read")) {
                            permission = "\\[GET]" + ADMIN_API_PATH + simpleGrantedAuthority.getAuthority().replace("read:", "") + "/.+";
                        } else if (simpleGrantedAuthority.getAuthority().startsWith("write")) {
                            permission = "\\[POST]" + ADMIN_API_PATH + simpleGrantedAuthority.getAuthority().replace("write:", "") + "/.*";
                        } else if (simpleGrantedAuthority.getAuthority().startsWith("delete")) {
                            permission = "\\[DELETE]" + ADMIN_API_PATH + simpleGrantedAuthority.getAuthority().replace("delete:", "") + "/.+";
                        } else if (simpleGrantedAuthority.getAuthority().startsWith("list")) {
                            permission = "\\[GET]" + ADMIN_API_PATH + simpleGrantedAuthority.getAuthority().replace("list:", "") + "/";
                        }
                        if (permission != null) {
                            //log.info("permission {} implies {} ?", permission, requestedResource);
                            matched = Pattern.matches(permission, requestedResource);
                        } else {
                            log.info("No permission found for resource {}", requestedResource);
                        }
                        return new AuthorizationDecision(matched);
                    })
                    .filter(AuthorizationDecision::isGranted)
                    .findAny().orElse(new AuthorizationDecision(false));
        } else
            return authorizationDecision;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        var securedEndpoint = AnnotationUtils.findAnnotation(bean.getClass(), SecuredEndpoint.class);
        if (bean instanceof EntityController || securedEndpoint != null) {
            // RestController restController = AnnotationUtils.findAnnotation(bean.getClass(), RestController.class);
            var requestMapping = AnnotationUtils.findAnnotation(bean.getClass(), RequestMapping.class);
            if (requestMapping != null || securedEndpoint != null) {
                Arrays.asList(requestMapping.value()).stream().filter(path -> path.startsWith(ADMIN_API_PATH)).forEach(path -> {
                    log.info("Secured Resource {}", path);
                    securedResources.add(path);
                });
            }
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }


    private AuthorizationDecision superAdminAuthzDecision(Authentication authentication) {

        if (authentication.isAuthenticated()) {
            log.info("checking for super admin access for user-details {} ", authentication.getPrincipal());
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            if (userDetails.getRoles().contains("ROLE_SYSTEM"))
                return new AuthorizationDecision(true);

            if (userDetails.getRoles().contains("ROLE_SUPER_ADMIN")) {
                return new AuthorizationDecision(true);
            }
        }
        return new AuthorizationDecision(false);
    }
}
