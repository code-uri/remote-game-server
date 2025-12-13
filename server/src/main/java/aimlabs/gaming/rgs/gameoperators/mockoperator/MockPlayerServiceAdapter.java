package aimlabs.gaming.rgs.gameoperators.mockoperator;

import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.IBrandService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameoperators.*;
import aimlabs.gaming.rgs.gamerounds.GameRound;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import aimlabs.gaming.rgs.transactions.TransactionType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
@Service
@Qualifier("mock")
public class MockPlayerServiceAdapter {

    public static final String CASH = "cash";
    public static final String CASH_ON_HOLD = "cash.onHold";
    public static final String CASH_TOTAL_AVAILABLE = "cash.totalAvailable";
    public static final String BONUS = "bonus";
    public static final String BONUS_ON_HOLD = "bonus.onHold";
    public static final String BONUS_TOTAL_AVAILABLE = "bonus.totalAvailable";
    public static final String TOTAL_AVAILABLE = "totalAvailable";
    public static final String DEMO_PLAYER = "demo-player";
    public static final String DEMO_CURRENCY = "USD";

    @Value("${app.player.mock.balance:10000}")
    private String demoBalance;

    @Value("${app.player.game-session.expiration.secs:900}")
    private int gameSessionExpirationSecs;

    @Autowired
    IBrandService brandService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ICurrencyService currencyService;

    private static Balance getBalance(Money cash, Money cashOnHold, Money cashAvailable) {
        Balance balance = new Balance();
        balance.setAmount(cash.getNumber().numberValueExact(BigDecimal.class));
        balance.setOnHold(cashOnHold.getNumber().numberValueExact(BigDecimal.class));
        balance.setTotal(cashAvailable.getNumber().numberValueExact(BigDecimal.class));
        return balance;
    }

    public PlayerInitialiseResponse playerInitialise(GameSession gameSession, String gameId) {
        Brand brand = brandService.findOneByTenantAndBrand(gameSession.getTenant(), gameSession.getBrand());
        
        if (brand == null) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST);
        }

        // Validate currency
        try {
            currencyService.getCurrency(gameSession.getCurrency());
        } catch (Exception e) {
            throw new BaseRuntimeException(SystemErrorCode.CURRENCY_NOT_SUPPORTED);
        }

        PlayerInitialiseResponse playerInfo = new PlayerInitialiseResponse();
        playerInfo.setExternalToken(gameSession.getToken());
        playerInfo.setPlayerId(gameSession.getPlayer());
        playerInfo.setCurrency(gameSession.getCurrency());

        Wallet wallet = balanceFromRedis(gameSession.getPlayer(), gameSession.getCurrency());
        if (wallet == null) {
            log.info("balance not found {}", gameSession.getPlayer());
            wallet = demoBalance(gameSession.getPlayer(), gameSession.getToken(), gameSession.getCurrency());
        }

        playerInfo.unWrapWallet(wallet);
        return playerInfo;
    }

    public Wallet playerBalance(GameSession gameSession) {
        Brand brand = brandService.findOneByTenantAndBrand(gameSession.getTenant(), gameSession.getBrand());
        
        if (brand == null) {
            log.info("Brand not found for tenant: {} and brand: {}", gameSession.getTenant(), gameSession.getBrand());
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST);
        }
        
        log.info("Found Brand {}", brand);

        Wallet wallet = balanceFromRedis(gameSession.getPlayer(), gameSession.getCurrency());
        if (wallet == null) {
            wallet = demoBalance(gameSession.getPlayer(), gameSession.getToken(), gameSession.getCurrency());
        }
        
        return wallet;
    }

    public PlayerTransactionResponse playerTransaction(GameSession gameSession, PlayerTransactionRequestV1 request) {
        String currency = request.getCurrency();

        log.info("Mock transaction {} ", request);
        
        if (request.getCredit() == null && request.getDebit() == null || request.getRequestType() == TransactionType.ROLLBACK) {
            request.setCredit(new PlayerGameTransaction(BigDecimal.valueOf(100), UUID.randomUUID().toString()));
        }

        log.info("fetch balance ");
        Wallet wallet = balanceFromRedis(gameSession.getPlayer(), currency);
        
        if (wallet == null) {
            throw new BaseRuntimeException(SystemErrorCode.PLAYER_NOT_FOUND);
        }

        log.info("transaction  {} ", wallet.getTotalBalance());

        PlayerTransactionResponse response;

        if (request.getDebit() != null && request.getDebit().getAmount().signum() > 0 
                && request.getCredit() != null && request.getCredit().getAmount().signum() > 0) {
            
            if (request.getDebit().getAmount().compareTo(wallet.getTotalBalance()) > 0) {
                throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
            }

            Map<String, Object> processedTxs = processDebitCredit(gameSession.getPlayer(),
                    request.getDebit(), request.getCredit());
            response = wrapAsResponse(gameSession.getPlayer(), currency);
            response.setProcessedTxnIds(processedTxs);
            
        } else if (request.getCredit() != null) {
            
            Map<String, Object> processedTxs = processCredit(gameSession.getPlayer(), request.getCredit());
            response = wrapAsResponse(gameSession.getPlayer(), currency);
            response.setProcessedTxnIds(processedTxs);
            
        } else {
            
            if (request.getDebit() != null && request.getDebit().getAmount().compareTo(wallet.getTotalBalance()) > 0) {
                throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
            }

            Map<String, Object> processedTxs = processDebit(gameSession.getPlayer(), request.getDebit());
            response = wrapAsResponse(gameSession.getPlayer(), currency);
            response.setProcessedTxnIds(processedTxs);
        }

        // Extend game session expiration
        redisTemplate.expire(gameSession.getToken(), gameSessionExpirationSecs, TimeUnit.SECONDS);

        return response;
    }

    public PlayerTransactionResponse rollback(GameSession gameSession, GameRound gameRound, PlayerTransactionRequestV1 request) {
        return playerTransaction(gameSession, request);
    }

    private Map<String, Object> processDebitCredit(String player, PlayerGameTransaction gtDebit, PlayerGameTransaction gtCredit) {
        Map<String, Object> processedTxs = new HashMap<>();
        
        doDebitCreditInRedis(player, getMinor(gtDebit.getAmount()), getMinor(gtCredit.getAmount()));
        
        processedTxs.put(gtDebit.getTxnId(), UUID.randomUUID().toString());
        processedTxs.put(gtCredit.getTxnId(), UUID.randomUUID().toString());
        
        return processedTxs;
    }

    private void doDebitCreditInRedis(String player, Long debit, Long credit) {
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        
        // Debit operations
        hashOps.increment(player, CASH, -debit);
        hashOps.increment(player, CASH_TOTAL_AVAILABLE, -debit);
        
        // Credit operations
        hashOps.increment(player, CASH, credit);
        hashOps.increment(player, CASH_TOTAL_AVAILABLE, credit);
    }

    private Map<String, Object> processDebit(String player, PlayerGameTransaction gt) {
        Map<String, Object> processedTxs = new HashMap<>();
        
        doDebitInRedis(player, getMinor(gt.getAmount()));
        
        processedTxs.put(gt.getTxnId(), UUID.randomUUID().toString());
        
        return processedTxs;
    }

    private void doDebitInRedis(String player, Long amount) {
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        
        hashOps.increment(player, CASH, -amount);
        hashOps.increment(player, CASH_TOTAL_AVAILABLE, -amount);
    }

    private Map<String, Object> processCredit(String player, PlayerGameTransaction gt) {
        Map<String, Object> processedTxs = new HashMap<>();
        
        doCreditInRedis(player, getMinor(gt.getAmount()));
        
        processedTxs.put(gt.getTxnId(), UUID.randomUUID().toString());
        
        return processedTxs;
    }

    private void doCreditInRedis(String player, Long amount) {
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        
        hashOps.increment(player, CASH, amount);
        hashOps.increment(player, CASH_TOTAL_AVAILABLE, amount);
    }

    private Wallet balanceFromRedis(String player, String currency) {
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        CurrencyUnit cu = Monetary.getCurrency(currency);

        Object cashObj = hashOps.get(player, CASH);
        Object cashOnHoldObj = hashOps.get(player, CASH_ON_HOLD);
        Object cashAvailableObj = hashOps.get(player, CASH_TOTAL_AVAILABLE);
        Object bonusObj = hashOps.get(player, BONUS);
        Object bonusOnHoldObj = hashOps.get(player, BONUS_ON_HOLD);
        Object bonusAvailableObj = hashOps.get(player, BONUS_TOTAL_AVAILABLE);

        // Check if any required field is missing
        if (cashObj == null || cashAvailableObj == null) {
            log.info("balanceFromRedis - incomplete data for player {}", player);
            return null;
        }

        Money cash = ofMinor(cu, cashObj);
        Money cashOnHold = cashOnHoldObj != null ? ofMinor(cu, cashOnHoldObj) : Money.ofMinor(cu, 0);
        Money cashAvailable = ofMinor(cu, cashAvailableObj);
        Money bonus = bonusObj != null ? ofMinor(cu, bonusObj) : Money.ofMinor(cu, 0);
        Money bonusOnHold = bonusOnHoldObj != null ? ofMinor(cu, bonusOnHoldObj) : Money.ofMinor(cu, 0);
        Money bonusAvailable = bonusAvailableObj != null ? ofMinor(cu, bonusAvailableObj) : Money.ofMinor(cu, 0);

        Balance cashBalance = getBalance(cash, cashOnHold, cashAvailable);
        Balance bonusBalance = getBalance(bonus, bonusOnHold, bonusAvailable);

        log.info("balanceFromRedis {}", player);
        Wallet wallet = new Wallet();
        wallet.setCash(cashBalance);
        wallet.setBonus(bonusBalance);
        wallet.setTotalBalance(wallet.getCash().getTotal().add(wallet.getBonus().getTotal()));
        wallet.setCurrency(currency);
        
        return wallet;
    }

    private PlayerTransactionResponse wrapAsResponse(String player, String currency) {
        Wallet playerWallet = balanceFromRedis(player, currency);
        
        if (playerWallet == null) {
            throw new BaseRuntimeException(SystemErrorCode.PLAYER_NOT_FOUND);
        }
        
        PlayerTransactionResponse response = new PlayerTransactionResponse();
        response.setWallet(playerWallet);
        return response;
    }

    public Wallet demoBalance(String player, String token, String currency) {
        log.info("demo balance reset {}", player);
        CurrencyUnit cu = Monetary.getCurrency(currency);
        
        MonetaryAmount demoAmount = Money.of(new BigDecimal(demoBalance), currency);
        MonetaryAmount zero = Money.ofMinor(cu, 0);

        Wallet wallet = new Wallet();
        wallet.setCurrency(currency);

        Balance cash = new Balance();
        cash.setAmount(demoAmount.getNumber().numberValueExact(BigDecimal.class));
        cash.setTotal(demoAmount.getNumber().numberValueExact(BigDecimal.class));
        cash.setOnHold(zero.getNumber().numberValueExact(BigDecimal.class));
        wallet.setCash(cash);

        Balance bonus = new Balance();
        bonus.setAmount(zero.getNumber().numberValueExact(BigDecimal.class));
        bonus.setTotal(zero.getNumber().numberValueExact(BigDecimal.class));
        bonus.setOnHold(zero.getNumber().numberValueExact(BigDecimal.class));
        wallet.setBonus(bonus);
        wallet.setTotalBalance(cash.getTotal().add(bonus.getTotal()));

        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();
        
        // Store all balance fields
        hashOps.put(player, CASH, getMinor(cash.getAmount()));
        hashOps.put(player, CASH_ON_HOLD, getMinor(cash.getOnHold()));
        hashOps.put(player, CASH_TOTAL_AVAILABLE, getMinor(cash.getTotal()));
        hashOps.put(player, BONUS, getMinor(bonus.getAmount()));
        hashOps.put(player, BONUS_ON_HOLD, getMinor(bonus.getOnHold()));
        hashOps.put(player, BONUS_TOTAL_AVAILABLE, getMinor(bonus.getTotal()));
        hashOps.put(player, TOTAL_AVAILABLE, getMinor(wallet.getTotalBalance()));
        
        // Set expiration on token
        redisTemplate.expire(token, gameSessionExpirationSecs, TimeUnit.SECONDS);

        return wallet;
    }

    private Money ofMinor(CurrencyUnit cu, Object o) {
        return Money.ofMinor(cu, Long.parseLong(o.toString()), 10);
    }

    private Long getMinor(BigDecimal amount) {
        return BigDecimal.valueOf(Math.pow(10, 10)).multiply(amount).longValue();
    }
}