package aimlabs.gaming.rgs.admin;

import aimlabs.gaming.rgs.security.UserDetails;
import aimlabs.gaming.rgs.core.EntityController;
import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
public class RestEndpointAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext>, BeanPostProcessor {

    public static final String ADMIN_API_PATH = "/admin/";

    @Autowired
    @Qualifier("securedResources")
    Set<String> securedResources;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();

        if (!auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }

        // Check for super admin
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        if (userDetails.getRoles().contains("ROLE_SYSTEM") || userDetails.getRoles().contains("ROLE_SUPER_ADMIN")) {
            return new AuthorizationDecision(true);
        }

        // Check permissions
        String requestedResource = context.getRequest().getMethod() + " " + context.getRequest().getRequestURI();
        Collection<SimpleGrantedAuthority> grantedAuthorityList = (Collection<SimpleGrantedAuthority>) auth
                .getAuthorities();

        for (SimpleGrantedAuthority authority : grantedAuthorityList) {
            String permission = null;
            if (authority.getAuthority().startsWith("read")) {
                permission = "\\[GET]" + ADMIN_API_PATH + authority.getAuthority().replace("read:", "") + "/.+";
            } else if (authority.getAuthority().startsWith("write")) {
                permission = "\\[POST]" + ADMIN_API_PATH + authority.getAuthority().replace("write:", "") + "/.*";
            } else if (authority.getAuthority().startsWith("delete")) {
                permission = "\\[DELETE]" + ADMIN_API_PATH + authority.getAuthority().replace("delete:", "") + "/.+";
            } else if (authority.getAuthority().startsWith("list")) {
                permission = "\\[GET]" + ADMIN_API_PATH + authority.getAuthority().replace("list:", "") + "/";
            }

            if (permission != null && Pattern.matches(permission, requestedResource)) {
                log.info("Authorization granted for {}", requestedResource);
                return new AuthorizationDecision(true);
            }
        }

        log.info("Authorization denied for {}", requestedResource);
        return new AuthorizationDecision(false);
    }

    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        var securedEndpoint = AnnotationUtils.findAnnotation(bean.getClass(), SecuredEndpoint.class);
        if (bean instanceof EntityController || securedEndpoint != null) {
            var requestMapping = AnnotationUtils.findAnnotation(bean.getClass(), RequestMapping.class);
            if (requestMapping != null || securedEndpoint != null) {
                Arrays.asList(requestMapping.value()).stream().filter(path -> path.startsWith(ADMIN_API_PATH))
                        .forEach(path -> {
                            log.info("Secured Resource {}", path);
                            securedResources.add(path);
                        });
            }
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}