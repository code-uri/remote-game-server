package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.core.entity.Status;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.IPlayerService;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.players.PlayerWallet;
import aimlabs.gaming.rgs.promotions.PromoBonus;
import aimlabs.gaming.rgs.promotions.PromotionType;
import aimlabs.gaming.rgs.transactions.Transaction;
import aimlabs.gaming.rgs.transactions.TransactionService;
import aimlabs.gaming.rgs.transactions.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;

import javax.money.CurrencyUnit;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static aimlabs.gaming.rgs.settings.GameSettingsService.isConfirmHandSupported;
import static aimlabs.gaming.rgs.transactions.TransactionType.CREDIT;

public class WinHandler implements GameHandler{

    @Autowired
    ICurrencyService currencyService;
    @Autowired
    private GameRoundService gameRoundService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private IPlayerService playerService;
    private GameHandler nextHandler;


    private void processGameRound(GamePlayResponse gamePlayResponse, GameSession gameSession,
                                  GameSkin gameSkin,
                                  Player player,
                                  Map<String, Object> settingsJsonNode) {

        CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

        Map<String, Object> updateMap = new HashMap<>();

        GameRound gameRound = gamePlayResponse.getGameRound();

        //        log.info("update game round  with status {}, isRoundCompleted {}", gameRound.getStatus(), gamePlayResponse.isRoundCompleted());
        // gameRound.setGamePlay(gamePlayResponse.getGamePlay().get("uid").asText());


        //noinspection RedundantIfStatement
        if (isConfirmHandSupported(settingsJsonNode))
            gameRound.setHandConfirmed(false);//hand confirmation api with update this to true later.
        else
            gameRound.setHandConfirmed(true);

        updateMap.put("handConfirmed", gameRound.isHandConfirmed());

        Money credit = Money.ofMinor(currencyUnit, 0);

        if (gamePlayResponse.getPromoBonus() != null) {
            PromoBonus promoBonus = gamePlayResponse.getPromoBonus();
            if (promoBonus.getPromotionType() == PromotionType.FREE_CREDITS) {

                credit = credit.add(Money.of(BigDecimal.valueOf(promoBonus.getCash()),
                        currencyUnit));
                gameRound.setPromoBonus(promoBonus);

                updateMap.put("promoBonus", gameRound.getPromoBonus());
            }
        }

        double wins = gamePlayResponse.getActivityWinnings();
        if (wins > 0) {
            credit = credit.add(Money.of(BigDecimal.valueOf(wins),
                    currencyUnit));
        } else if (gamePlayResponse.getWinnings() > 0) {
            credit = credit.add(Money.of(BigDecimal.valueOf(gamePlayResponse.getWinnings()),
                    currencyUnit));
        }
//                    if (gameRound.getJackpotDetails() != null
//                            && gameRound.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency() != null
//                            && gameRound.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency().isPositive()) {
//                        credit = credit.add(gameRound.getJackpotDetails().getTotalJackpotWinningsInPlayerCurrency());
//                    }


        try {

            Transaction transaction = null;
            if (credit.isPositive() || gamePlayResponse.isRoundCompleted()) {
                transaction = processGameTransaction(gameSession, player, gameRound, gamePlayResponse.getGameActivityUid(), null, credit, CREDIT, null, null, null, false,
                        gamePlayResponse.isRoundCompleted());
            }
            gameRound.setTotalWin(gameRound.getTotalWin().add(credit));

            gameRound.setStatus(determineStatus(gamePlayResponse));
            updateMap.put("status", gameRound.getStatus());

            gamePlayResponse.addTotalWinnings(gameRound.getTotalWin().getNumber().doubleValueExact());

            PlayerWallet playerWallet = transaction != null ? transaction.getWallet() : null;
            if (playerWallet == null)
                playerWallet = playerService.getBalance(gameSession, player.getCorrelationId());


            gameRound = gameRoundService.updatePartial(gameRound.getUid(), updateMap);
            updateGamePlayResponse(gamePlayResponse, gameRound, playerWallet);
        } catch (Exception e) {
            gameRoundService.updateStatusAndReturnError(gamePlayResponse.getGameRoundId(), e);
        } finally {
            releaseGameRoundLock(gameRound);
        }
    }

    public Transaction processGameTransaction(GameSession gameSession, Player player, GameRound gameRound,
                                              String gameActivityUid, Money debit, Money credit,
                                              TransactionType txnType, String extTxnId, String orgTxnId, Money orgTxnAmount,
                                              boolean rollbackRequired, boolean gameRoundCompleted) {
        return transactionService.processGameTransaction(player.getCorrelationId(), gameRound, gameActivityUid, gameSession,
                debit, credit, txnType, extTxnId, orgTxnId, orgTxnAmount, rollbackRequired, gameRoundCompleted);
    }

    private Status determineStatus(GamePlayResponse gamePlayResponse) {
        return gamePlayResponse.isRoundCompleted() ? Status.COMPLETED : Status.valueOf(gamePlayResponse.getGamePlayStatus());
    }

    private void releaseGameRoundLock(GameRound gameRound) {
        gameRoundService.releaseLockOnGameRound(gameRound);
    }

    private GamePlayResponse updateGamePlayResponse(GamePlayResponse gamePlayResponse, GameRound gameRound, PlayerWallet playerWallet) {
        gamePlayResponse.setGameRound(gameRound);
        gamePlayResponse.setPlayerWallet(playerWallet);
        return gamePlayResponse;
    }

    @Override
    public void handle(JsonNode request, GamePlayContext gamePlayContext) {
        GamePlayResponse gamePlayResponse = gamePlayContext.getEngineResponse();
        GameSession gameSession = gamePlayContext.getGameSession();
        GameSkin gameSkin = gamePlayContext.getGameSkin();
        Player player = gamePlayContext.getPlayer();
        Map<String, Object> settingsJsonNode = gamePlayContext.getSettings();

        processGameRound(gamePlayResponse, gameSession, gameSkin, player, settingsJsonNode);


        this.nextHandler.handle(request, gamePlayContext);

    }

    @Override
    public void setNext(GameHandler nextHandler) {
        this.nextHandler = nextHandler;
    }
}
