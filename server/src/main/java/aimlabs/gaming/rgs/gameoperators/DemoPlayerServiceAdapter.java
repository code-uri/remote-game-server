// java
package aimlabs.gaming.rgs.gameoperators;

import aimlabs.gaming.rgs.brands.IBrandService;

import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamesessions.GameSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
@Component
@Qualifier("demo")
public class DemoPlayerServiceAdapter implements PlayerAccountManager {

    public static final String TOTAL_AVAILABLE = "totalAvailable";
    public static final String DEMO_PLAYER = "demo-player";
    public static final String DEMO_CURRENCY = "USD";

    @Value("${app.player.game-session.expiration.secs:900}")
    private int gameSessionExpirationSecs;

    @Autowired
    private IBrandService brandService;

    // blocking Redis
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // optional: if you really need Mongo in future (kept for parity)
    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    private static Balance getBalance(Money money) {
        Balance balance = new Balance();
        balance.setAmount(money.getNumber().numberValueExact(BigDecimal.class));
        balance.setTotal(money.getNumber().numberValueExact(BigDecimal.class));
        return balance;
    }

    @Override
    public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
        PlayerInitialiseResponse response = new PlayerInitialiseResponse();
        response.setPlayerId(request.getSessionToken());
        response.setExternalToken(request.getSessionToken());

        Wallet wallet = createDemoBalance(request.getSessionToken(),
                                          request.getBrand(),
                                          request.getTenant());

        response.setCurrency(wallet.getCurrency());
        response.unWrapWallet(wallet);
        return response;
    }

    @Override
    public Wallet playerBalance(PlayerBalanceRequest request) {
        var brand = brandService.findOneByTenantAndBrand(request.getTenant(), request.getBrand());
        if (brand == null) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_BRAND,
                    "Brand not found for tenant=" + request.getTenant()
                            + ", brand=" + request.getBrand());
        }
        return balanceFromRedis(request.getToken(), brand.getCurrency());
    }

    @Override
    public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
        String token = request.getToken();
        String currency = request.getCurrency();

        // balance inquiry only
        if (request.getCredit() == null && request.getDebit() == null) {
            PlayerBalanceRequest balanceRequest = new PlayerBalanceRequest();
            balanceRequest.setToken(request.getToken());
            balanceRequest.setBrand(request.getBrand());
            balanceRequest.setTenant(request.getTenant());
            balanceRequest.setCurrency(request.getCurrency());
            balanceRequest.setGameId(request.getCurrency());

            Wallet wallet = playerBalance(balanceRequest);
            if (wallet == null) {
                throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
            }

            PlayerTransactionResponse res = new PlayerTransactionResponse();
            res.setWallet(wallet);
            touchSessionExpiry(token);
            return res;
        }

        Wallet wallet = balanceFromRedis(token, currency);
        if (wallet == null) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
        }

        Map<String, Object> processedTxns;
        Double debit = request.getDebit();
        Double credit = request.getCredit();

        if (debit != null && debit > 0 && credit != null && credit > 0) {
            if (debit > wallet.getTotalBalance().doubleValue()) {
                throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
            }
            processedTxns = processDebitCredit(request.getTxnId(), token, debit, credit);
        } else if (credit != null && credit > 0) {
            processedTxns = processCredit(request.getTxnId(), token, credit);
        } else {
            if (debit != null && debit > wallet.getTotalBalance().doubleValue()) {
                throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE);
            }
            processedTxns = processDebit(request.getTxnId(), token, debit);
        }

        Wallet updatedWallet = balanceFromRedis(token, currency);
        if (updatedWallet == null) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                    "Wallet disappeared after processing transaction");
        }

        PlayerTransactionResponse response = new PlayerTransactionResponse();
        response.setWallet(updatedWallet);
        response.setProcessedTxnIds(processedTxns);

        touchSessionExpiry(token);
        return response;
    }

    @Override
    public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
        // for demo just reuse transaction logic
        return playerTransaction(request);
    }

    @Override
    public void keepSessionAlive(GameSession session) {
        // demo: just extend TTL if key exists
        String token = session.getUid();
        touchSessionExpiry(token);
    }

    // ---- internal helpers (blocking) ----

    private void touchSessionExpiry(String token) {
        try {
            redisTemplate.expire(normalizeToken(token),
                    gameSessionExpirationSecs,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to update TTL for demo token {}", token, e);
        }
    }

    private Map<String, Object> processDebitCredit(String txnId,
                                                   String token,
                                                   Double gtDebit,
                                                   Double gtCredit) {
        String key = normalizeToken(token);
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();

        Long debitMinor = getMinor(gtDebit);
        Long creditMinor = getMinor(gtCredit);

        Long current = readMinorBalance(ops, key);
        long newValue = current - debitMinor + creditMinor;
        ops.put(key, TOTAL_AVAILABLE, newValue);

        Map<String, Object> pTxs = new HashMap<>();
        pTxs.put(txnId, UUID.randomUUID().toString());
        return pTxs;
    }

    private Map<String, Object> processDebit(String txnId,
                                             String token,
                                             Double amount) {
        String key = normalizeToken(token);
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();

        Long debitMinor = getMinor(amount);
        Long current = readMinorBalance(ops, key);
        long newValue = current - debitMinor;
        ops.put(key, TOTAL_AVAILABLE, newValue);

        Map<String, Object> pTxs = new HashMap<>();
        pTxs.put(txnId, UUID.randomUUID().toString());
        return pTxs;
    }

    private Map<String, Object> processCredit(String txnId,
                                              String token,
                                              Double amount) {
        String key = normalizeToken(token);
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();

        Long creditMinor = getMinor(amount);
        Long current = readMinorBalance(ops, key);
        long newValue = current + creditMinor;
        ops.put(key, TOTAL_AVAILABLE, newValue);

        Map<String, Object> pTxs = new HashMap<>();
        pTxs.put(txnId, UUID.randomUUID().toString());
        return pTxs;
    }

    private Long readMinorBalance(HashOperations<String, Object, Object> ops, String key) {
        Object val = ops.get(key, TOTAL_AVAILABLE);
        if (val == null) {
            throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
        }
        return Long.parseLong(val.toString());
    }

    private Wallet balanceFromRedis(String token, String currency) {
        String key = normalizeToken(token);
        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();

        Object raw = ops.get(key, TOTAL_AVAILABLE);
        if (raw == null) {
            return null;
        }

        CurrencyUnit cu = Monetary.getCurrency(currency);
        Money money = ofMinor(cu, raw);
        Balance cash = getBalance(money);

        Wallet wallet = new Wallet();
        wallet.setCash(cash);
        wallet.setTotalBalance(cash.getTotal());
        wallet.setCurrency(currency);
        return wallet;
    }

    private Wallet createDemoBalance(String sessionToken, String brandUid, String tenant) {
        String token = normalizeToken(sessionToken);

        var brand = brandService.findOneByTenantAndBrand(tenant, brandUid);
        if (brand == null) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_BRAND,
                    "Brand not found for tenant=" + tenant + ", brand=" + brandUid);
        }

        String currency = brand.getCurrency();
        CurrencyUnit cu = Monetary.getCurrency(currency);
        MonetaryAmount demoAmount = Money.of(brand.getDemoBalance(), cu);

        Wallet wallet = new Wallet();
        wallet.setCurrency(currency);

        Balance cash = new Balance();
        cash.setAmount(demoAmount.getNumber().numberValueExact(BigDecimal.class));
        cash.setTotal(demoAmount.getNumber().numberValueExact(BigDecimal.class));
        wallet.setCash(cash);
        wallet.setTotalBalance(cash.getTotal());

        HashOperations<String, Object, Object> ops = redisTemplate.opsForHash();
        ops.put(token, TOTAL_AVAILABLE, getMinor(wallet.getTotalBalance().doubleValue()));

        redisTemplate.expire(token, gameSessionExpirationSecs, TimeUnit.SECONDS);
        return wallet;
    }

    private String normalizeToken(String sessionToken) {
        return sessionToken.toLowerCase().replace("demo-", "");
    }

    private Money ofMinor(CurrencyUnit cu, Object o) {
        return Money.ofMinor(cu, Long.parseLong(o.toString()), 4);
    }

    private Long getMinor(Double amount) {
        return BigDecimal.valueOf(10000)
                .multiply(BigDecimal.valueOf(amount))
                .longValue();
    }
}