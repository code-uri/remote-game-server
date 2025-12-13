package aimlabs.gaming.rgs.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;

public class AdminUserAuthenticationToken implements Authentication {

    private UserDetails principal;
    private Object credentials;
    private Collection<? extends GrantedAuthority> authorities = new ArrayList<>();
    private boolean authenticated;

    public AdminUserAuthenticationToken(UserDetails principal, String token) {
        this.principal = principal;
        this.credentials = token;
        this.authenticated = true;
    }

    
    public String getName() {
        UserDetails as = getPrincipal();
        if(as instanceof UserDetails){
            return as.getId();
        }
        return "";
    }

    
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    
    public Object getCredentials() {
        return this.credentials;
    }

    
    public Object getDetails() {
        return null;
    }

    
    public UserDetails getPrincipal() {
        return this.principal;
    }

    
    public boolean isAuthenticated() {
        return this.authenticated;
    }

    
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

}
