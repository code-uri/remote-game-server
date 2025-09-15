//package aimlabs.gaming.rgs.security;
//
//public class PlayerAuthenticationEntryPoint extends HttpBasicServerAuthenticationEntryPoint {
//    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
//    private static final String DEFAULT_REALM = "Access to game client";
//    private static final String WWW_AUTHENTICATE_FORMAT = "Bearer realm=\"%s\"";
//
//
//    private String headerValue = createHeaderValue(DEFAULT_REALM);
//
//    public PlayerAuthenticationEntryPoint() {
//        super();
//    }
//
//    private static String createHeaderValue(String realm) {
//        Assert.notNull(realm, "realm cannot be null");
//        return String.format(WWW_AUTHENTICATE_FORMAT, realm);
//    }
//
//
//    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException e) {
//        return Mono.fromRunnable(() -> {
//            ServerHttpResponse response = exchange.getResponse();
//            response.setStatusCode(HttpStatus.UNAUTHORIZED);
//            response.getHeaders().set(WWW_AUTHENTICATE, this.headerValue);
//        });
//    }
//
//    /**
//     * Sets the realm to be used
//     *
//     * @param realm the realm. Default is "Realm"
//     */
//    public void setRealm(String realm) {
//        this.headerValue = createHeaderValue(realm);
//    }
//}
