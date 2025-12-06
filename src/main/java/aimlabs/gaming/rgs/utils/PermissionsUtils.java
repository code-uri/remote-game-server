package in.aimlabs.gaming.permissions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;

import java.util.regex.Pattern;

@Slf4j
public class PermissionsUtils {

   public static String getPermission(AuthorizationContext authorizationContext){
       Object id = authorizationContext.getVariables().get("id");
       ServerWebExchange exchange = authorizationContext.getExchange();

       String api = exchange.getRequest().getPath().pathWithinApplication().value();
       return "["+exchange.getRequest().getMethod()+"]"+api;
    }


    public static void main(String[] args) {

       //read user
        System.out.println(Pattern.matches("\\[GET]/admin/permissions/.+", "[GET]/admin/permissions/"));
        //read all users
        //System.out.println(Pattern.matches("\\[GET]/admin/users/", "[GET]/admin/users/"));
        //write user
        //System.out.println(Pattern.matches("\\[POST]/admin/users/.*", "[POST]/admin/users/"));
        //write user
        //System.out.println(Pattern.matches("\\[POST]/admin/users/.*", "[POST]/admin/users/1231"));
        //delete user
        //System.out.println(Pattern.matches("\\[DELETE]/admin/users/.+", "[DELETE]/admin/users/123123"));

    }
}
