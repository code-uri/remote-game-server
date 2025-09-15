package aimlabs.gaming.rgs.security;

import aimlabs.gaming.rgs.games.GameSessionBearerTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Slf4j
public class AdminJWTAuthenticationWebFilter extends AuthenticationFilter {
    public AdminJWTAuthenticationWebFilter(AuthenticationManager authenticationManager,
                                           RequestMatcher requestMatcher,
                                           GameSessionBearerTokenProvider bearerTokenProvider) {
        super(authenticationManager, bearerTokenProvider);
        setRequestMatcher(requestMatcher);
    }

    public AdminJWTAuthenticationWebFilter(AuthenticationManager authenticationManager, GameSessionBearerTokenProvider bearerTokenProvider) {
        super(authenticationManager, bearerTokenProvider);
    }

//    public AdminJWTAuthenticationWebFilter(ReactiveAuthenticationManager authenticationManager,
//                                           ServerWebExchangeMatcher serverWebExchangeMatcher,
//                                      TokenProvider tokenProvider,
//                                      ServerAuthenticationSuccessHandler serverAuthenticationSuccessHandler) {
//        super(authenticationManager);
//        setRequiresAuthenticationMatcher(serverWebExchangeMatcher);
//        setServerAuthenticationConverter(tokenProvider);
//        setAuthenticationSuccessHandler(serverAuthenticationSuccessHandler);
//    }
//

}