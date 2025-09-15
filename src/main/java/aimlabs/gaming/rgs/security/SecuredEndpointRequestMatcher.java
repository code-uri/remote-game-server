package aimlabs.gaming.rgs.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;
import java.util.stream.Stream;

class SecuredEndpointRequestMatcher implements RequestMatcher {


    private final List<String> securedResources;

    SecuredEndpointRequestMatcher(List<String> securedResources) {
            this.securedResources = securedResources;

        }
        @Override
        public boolean matches(HttpServletRequest request) {
            return false;
        }

        @Override
        public MatchResult matcher(HttpServletRequest request) {
            PathPatternRequestMatcher[] requestMatchers = securedResources.stream()
                    .flatMap(s -> Stream.of(PathPatternRequestMatcher.withDefaults().matcher(s),
                            PathPatternRequestMatcher.withDefaults().matcher(s + "/{id}"),
                            PathPatternRequestMatcher.withDefaults().matcher(s + "/*")))
                    .toArray(PathPatternRequestMatcher[]::new);

            return new OrRequestMatcher(requestMatchers).matcher(request);
        }
}