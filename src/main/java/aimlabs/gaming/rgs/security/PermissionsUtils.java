package aimlabs.gaming.rgs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;

@Slf4j
public class PermissionsUtils {

   public static String getPermission(RequestAuthorizationContext authorizationContext){
       //Object id = authorizationContext.getVariables().get("id");


       String api = authorizationContext.getRequest().getPathInfo();
       return "["+authorizationContext.getRequest().getMethod()+"]"+api;
    }
}
