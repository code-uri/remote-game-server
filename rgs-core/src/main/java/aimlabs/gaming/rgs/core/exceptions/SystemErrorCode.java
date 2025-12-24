package aimlabs.gaming.rgs.core.exceptions;

public enum SystemErrorCode implements ErrorCode {
    GENERAL_API_ERROR("general.api.error", "General API Error", 400),
    NOT_IMPLEMENTED("system.notimplemented", "Not Implemented", 500),
    SYSTEM_ERROR("system.error", "System error", 500),
    NOT_FOUND("resource.not.found", "Resource not found", 404),
    INVALID_BRAND("invalid.brand", "Invalid brand", 400),
    INVALID_GAME("invalid.game", "Invalid game", 400),
    INVALID_REQUEST("invalid.request", "Invalid request", 400),
    COM_ERROR("communication.error", "Communication Error", 500),
    INVALID_TOKEN("invalid.token", "Invalid brand", 400),
    TOKEN_EXPIRED("token.expired", "Token expired", 400),
    CURRENCY_NOT_SUPPORTED("currency.not.supported","Currency not supported" , 400 ),
    PLAYER_NOT_FOUND( "player.not.found", "Player not found", 400 ),
    EMPTY_RESPONSE("empty.response", "Empty Response!", 400),
    INTERNAL_ERROR("internal.server.error", "Game server internal error", 500),
    INACTIVE_GAME("inactive.game", "Inactive game", 400),
    GAME_COMING_SOON("game.coming.soon", "Coming soon!", 400),
    TOKEN_INVALID("token.invalid", "Token invalid", 401),
    INVALID_STAKE("invalid.stake", "Invalid Stake", 400),
    INVALID_GAME_ROUND("invalid.round", "Invalid Game Round", 400),
    GAME_ROUND_CLOSED("gameround.closed", "Game Round Closed", 400),
    ROLLBACK_GAME_ROUND("rollback.round", "Rollback Game Round", 400),
    GAME_ROUND_CANCELLED("round.cancelled", "Game Round Cancelled", 400),
    INSUFFICIENT_BALANCE("insufficient.balance", "Insufficient Balance", 400),
    INVALID_GAME_CONFIGURATION("invalid.game.configuration","Invalid game configuration", 400),
    DEMO_GAME_NOT_ALLOWED("demo.game.not.allowed","Demo game not allowed", 400),
    GAME_CONFIGURATION_NOT_SUPPORTED("game.configuration.not.supported","Game configuration not supported", 400),
    TRANSACTION_ALREADY_EXISTS("transaction.already.exists","Transaction Already Exists", 400),
    USER_NOT_FOUND("USER_NOT_FOUND","USER_NOT_FOUND",400),
    USER_ALREADY_REGISTERED("user.already.registered","User already registered", 400),
    INVALID_PROMOTION_REFERENCE("invalid.promotion.reference","invalid promotion reference", 400),
    PLAYER_REQUIRED_IN_REQUEST("player.required","player required in the request", 400),
    INVALID_FREE_SPINS_REQUEST("invalid.freespins.request","invalid freespins request", 400),
    FREE_SPINS_ALREADY_CLAIMED("freespins.already.claimed","Free spins already claimed", 400),
    FREE_SPINS_CLAIM_ROLLBACK_FAILED("freespins.claim.rollback.failed","freespins claim rollback failed", 500),
    INVALID_CLIENT_ID("invalid.clientId","invalid clientId", 400),
    RETRY_REQUEST("retry.request", "Retry request", 400),
    CONNECTOR_NOT_FOUND("connector.not.found", "Connector not found!", 400);

    String code;
    String description;
    int httpStatusCode;

    SystemErrorCode(String code, String description, int httpStatusCode) {
        this.code = code;
        this.description = description;
        this.httpStatusCode = httpStatusCode;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return this.description;
    }

    public int getHttpStatusCode() {
        return this.httpStatusCode;
    }

}
