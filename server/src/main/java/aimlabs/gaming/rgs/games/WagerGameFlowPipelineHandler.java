package aimlabs.gaming.rgs.games;

import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamerounds.GameRoundService;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.players.Player;
import aimlabs.gaming.rgs.transactions.Transaction;
import aimlabs.gaming.rgs.transactions.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.money.CurrencyUnit;
import java.math.BigDecimal;
import java.util.Map;

import static aimlabs.gaming.rgs.settings.GameSettingsService.isConfirmHandSupported;
import static aimlabs.gaming.rgs.transactions.TransactionType.DEBIT;

@Getter
@Setter
@Component
@Slf4j
public class WagerGameFlowPipelineHandler implements GameFlowPipelineHandler {
    private GameFlowPipelineHandler nextHandler;

    @Autowired
    private GameRoundService gameRoundService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private ICurrencyService currencyService;

    @Override
    public void handle(JsonNode request, GamePlayContext gamePlayContext) {

        GamePlayResponse gamePlayResponse = gamePlayContext.getEngineResponse();
        GameSession gameSession = gamePlayContext.getGameSession();
        GameSkin gameSkin = gamePlayContext.getGameSkin();
        Player player = gamePlayContext.getPlayer();
        Map<String, Object> settingsJsonNode = gamePlayContext.getSettings();

        log.info("wager filter handing");
        if (gamePlayResponse.isNewRound()) {
            handleNewRound(gamePlayResponse, gameSession, gameSkin, player, settingsJsonNode);
        } else {
            handleExistingRound(gamePlayResponse, gameSession, player);
        }

        if (nextHandler != null) {
            nextHandler.handle(request, gamePlayContext);
        }
    }

    @Override
    public void setNext(GameFlowPipelineHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    private void handleNewRound(GamePlayResponse gamePlayResponse,
            GameSession gameSession,
            GameSkin gameSkin,
            Player player,
            Map<String, Object> settingsJsonNode) {
        JsonNode gamePlay = gamePlayResponse.getGamePlay();
        String uid = gamePlay.get("uid").asText();

        BigDecimal wager = getWager(gamePlayResponse, gameSession, gameSkin.getUid());

        log.info("createGameRound wager {}", wager);
        Money totalWagerMoney = Money.of(wager.doubleValue(), gameSession.getCurrency());
        GameRound gameRound = gameRoundService.createGameRound(
                gamePlayResponse.getGameRoundId(),
                null,
                gameSession,
                gameSkin.getUid(),
                gameSkin.getGameType(),
                player,
                uid,
                wager,
                gamePlayResponse.getGameActivityUid(),
                false,
                gamePlayResponse,
                isConfirmHandSupported(settingsJsonNode));

        try {

            Transaction transaction = transactionService.processGameTransaction(player.getCorrelationId(), gameRound,
                    gamePlayResponse.getGameActivityUid(),
                    gameSession,
                    totalWagerMoney,
                    null,
                    DEBIT, null, null, null, true, gamePlayResponse.isRoundCompleted());

            gamePlayResponse.setGameRound(gameRound);
            gamePlayResponse.setPlayerWallet(transaction.getWallet());

        } catch (Exception e) {
            gameRoundService.updateStatusAndReturnError(gamePlayResponse.getGameRoundId(), e);
        }
    }

    private BigDecimal getWager(GamePlayResponse gamePlayResponse, GameSession gameSession, String gameId) {
        return BigDecimal.valueOf(gamePlayResponse.getTotalWager());
    }

    private void handleExistingRound(GamePlayResponse gamePlayResponse, GameSession gameSession, Player player) {
        double additionalWager = gamePlayResponse.getAdditionalWager();
        if (additionalWager > 0) {
            CurrencyUnit currencyUnit = currencyService.getCurrency(gameSession.getCurrency());

            Money debit = Money.of(additionalWager, currencyUnit);
            Transaction transaction = transactionService.processGameTransaction(
                    player.getCorrelationId(),
                    gamePlayResponse.getGameRound(),
                    gamePlayResponse.getGameActivityUid(),
                    gameSession,
                    debit,
                    null,
                    DEBIT,
                    null,
                    null,
                    null,
                    false,
                    gamePlayResponse.isRoundCompleted());
        }
    }
}
