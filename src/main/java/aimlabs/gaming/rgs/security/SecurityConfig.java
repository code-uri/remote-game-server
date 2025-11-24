package aimlabs.gaming.rgs.security;

import aimlabs.gaming.rgs.core.utils.JwtUtil;
import aimlabs.gaming.rgs.games.GameSessionBearerTokenProvider;
import aimlabs.gaming.rgs.gamesessions.GameSessionAuthenticationManager;
import aimlabs.gaming.rgs.permissions.PermissionService;
import aimlabs.gaming.rgs.roles.Role;
import aimlabs.gaming.rgs.roles.RoleService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
@Import(SecuredResourcesConfig.class)
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private static final String[] AUTH_WHITELIST = {
            "/admin/users/login",
//            "/api/rgs/admin/users/login",
//            "/api/rgs/admin/brand-gaming",
            "/admin/brand-gaming",
            "/admin/users/refresh",
//            "/api/rgs/admin/users/refresh",
            "/admin/cache/**",
            "/authorize/**",
            "/favicon.ico"
/*            "/resources/**",
            "/webjars/**",
            "/authorize/**",
            "/favicon.ico",*/
    };
    @Autowired
    JWTClientProperties jjwt;
    @Autowired
    RoleService roleService;
    @Autowired
    CorsProperties corsProperties;

    @Autowired
    PermissionService permissionService;

    private static Pattern initPattern(String patternValue) {
        String portList = null;
        Matcher matcher = Pattern.compile("(.*):\\[(\\*|\\d+(,\\d+)*)]").matcher(patternValue);
        if (matcher.matches()) {
            patternValue = matcher.group(1);
            portList = matcher.group(2);
        }

        patternValue = "\\Q" + patternValue + "\\E";
        patternValue = patternValue.replace("*", "\\E.*\\Q");
        if (portList != null) {
            patternValue = patternValue + (portList.equals("*") ? "(:\\d+)?" : ":(" + portList.replace(',', '|') + ")");
        }

        return Pattern.compile(patternValue);
    }

    @Bean
    static RoleHierarchy roleHierarchy() {
        DefaultRoleHierarchy hierarchy = new DefaultRoleHierarchy();
        hierarchy.setHierarchy("ROLE_SYSTEM > ROLE_SUPER_ADMIN \n ROLE_SUPER_ADMIN > ROLE_ADMIN \n ROLE_ADMIN > ROLE_STAFF");
        return hierarchy;
    }


    @Bean
    JwtUtil jwtUtil(JWTClientProperties jjwt) {
        return new JwtUtil(jjwt.getClientId(), jjwt.getClientSecret());
    }

    @Bean
    GameSessionBearerTokenProvider bearerTokenProvider() {
        return new GameSessionBearerTokenProvider(jwtUtil(jjwt));
    }

    @Bean
    RestEndpointAuthorizationManager authorizationManager() {
        return new RestEndpointAuthorizationManager();
    }

   @Bean
   SecuredEndpointRequestMatcher securedEndpointRequestMatcher(RestEndpointAuthorizationManager manager) {
       return new SecuredEndpointRequestMatcher(manager.getSecuredResources().stream().toList());
   }

    @Bean
    AuthenticationManager authenticationManager() {
        return new GameSessionAuthenticationManager();
    }

    @Bean
    SecurityFilterChain apiRGSFilterChain(HttpSecurity http) {
        AuthenticationFilter authenticationFilter = new AuthenticationFilter(authenticationManager(),
                bearerTokenProvider());
        authenticationFilter.setRequestMatcher(request -> Arrays.stream(AUTH_WHITELIST)
                .noneMatch(path -> request.getPathInfo().startsWith(path)
                                   || request.getRequestURI().startsWith(path)));
        try {
            return http
                    .securityMatcher("/**")
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> configurationSource())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .headers(headersConfigurer
                            -> headersConfigurer
                            .addHeaderWriter(new StaticHeadersWriter("Access-Control-Allow-Origin",
                                    "*")))

                    .addFilterAt(authenticationFilter, AuthenticationFilter.class)
                    .authorizeHttpRequests(authorizeHttpRequests
                            -> authorizeHttpRequests.requestMatchers("/**")
                            .permitAll()
                            .requestMatchers("/api/rgs/connect/**")
                            .permitAll()
                            .anyRequest()
                            .authenticated())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    SecurityFilterChain apiA8RFilterChain(HttpSecurity http) {
        try {
            return http
                    .securityMatcher("/connect/**")
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> configurationSource())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests.anyRequest().permitAll())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain adminApiFilterChain(HttpSecurity http) {
        try {
            AuthorizationManager<RequestAuthorizationContext> authorizationManager = authorizationManager();
            return http
                    .securityMatcher("/admin/**")
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .csrf(AbstractHttpConfigurer::disable)
                    .cors(cors -> configurationSource())
                    .formLogin(AbstractHttpConfigurer::disable)
                    .addFilterAt(new AdminJWTAuthenticationWebFilter(authenticationManager(),
                                    new GameSessionBearerTokenProvider(jwtUtil(jjwt)) {

                                        public Authentication getAuthentication(String token) {

                                            Claims claims = getJwtUtil().decodeJWT(token);
                                            String sub = claims.get("sub", String.class);
                                            String tenant = sub.split("\\|")[0];
                                            String identity = sub.split("\\|")[1];
                                            List<String> roles = List.of(claims.get("roles", String.class).split("\\|"));

                                            List<Role> roles1 = roleService.findAllByUidIn(roles);
                                            List<String> permissions = roles1.stream().flatMap(role -> {

                                                        return permissionService.findByUserId(identity).stream()
                                                                .map(permission -> permission.getName());
                                                    })
                                                    .toList();


                                            AdminUserAuthenticationToken authenticationToken =
                                                    new AdminUserAuthenticationToken(
                                                            new UserDetails(tenant, identity, roles), token);
                                            Collection<SimpleGrantedAuthority> authorities = permissions.stream().map(SimpleGrantedAuthority::new)
                                                    .collect(Collectors.toCollection(
                                                            ArrayList<SimpleGrantedAuthority>::new));
                                            authorities.addAll(roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
                                            authenticationToken.setAuthorities(authorities);
                                            authenticationToken.setAuthenticated(true);
                                            return authenticationToken;
                                        }
                                    }),
                            AuthenticationFilter.class)
                    .authorizeHttpRequests(authorizeHttpRequests
                            -> authorizeHttpRequests
                            .requestMatchers("/admin/forced-results",
                                    "/admin/users/login",
                                    "/admin/users/refresh",
                                    "/admin/brand-games/**",
                                    "/admin/games/reports/s3")
                            .permitAll()
                            .requestMatchers("/admin/configurations",
                                    "/admin/configurations/*")
                            .authenticated()
                            .requestMatchers(securedEndpointRequestMatcher(authorizationManager()))
                            .access(authorizationManager)
                            .anyRequest()
                            .authenticated())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    CorsConfigurationSource configurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        log.info("Check RGS Cors {}", corsProperties.getEndpoints().size());
        if (corsProperties.getEndpoints().isEmpty()) {
            log.info("Empty RGS Cors");
        }

        corsProperties.getEndpoints().forEach((entry) -> {
            log.info("RGS Cors Entry {}", entry);
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOriginPatterns(Arrays.asList(entry.getAllowedOrigins()));
            configuration.addAllowedOrigin("http://localhost:3000");
            configuration.setAllowedMethods(Arrays.asList(entry.getAllowedMethods().split(",")));
            configuration.setAllowedHeaders(Arrays.asList(entry.getAllowedHeaders().split(",")));
            configuration.setExposedHeaders(Arrays.asList(entry.getExposedHeaders().split(",")));


            log.info("allowed origins {} ", configuration.getAllowedOrigins());
            source.registerCorsConfiguration(entry.getPath(), configuration);
        });

        return source;
    }
}