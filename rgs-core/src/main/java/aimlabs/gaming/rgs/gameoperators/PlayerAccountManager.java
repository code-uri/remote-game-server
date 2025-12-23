package aimlabs.gaming.rgs.gameoperators;


import aimlabs.gaming.rgs.gamesessions.GameSession;

public interface PlayerAccountManager {

    PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request);

    Wallet playerBalance(PlayerBalanceRequest request);

    PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request);

    PlayerTransactionResponse rollback(PlayerTransactionRequest request);

    default void closeSession(PlayerBalanceRequest request) {
        throw new UnsupportedOperationException();
    }

    default void keepSessionAlive(GameSession gameSession) {
        throw new UnsupportedOperationException();
    }

}
