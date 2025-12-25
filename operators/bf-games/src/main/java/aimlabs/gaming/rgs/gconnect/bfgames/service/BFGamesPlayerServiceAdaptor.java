package aimlabs.gaming.rgs.gconnect.bfgames.service;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gameoperators.Balance;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManager;
import aimlabs.gaming.rgs.gameoperators.PlayerAccountManagerFactory;
import aimlabs.gaming.rgs.gameoperators.PlayerBalanceRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerInitialiseRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerInitialiseResponse;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionRequest;
import aimlabs.gaming.rgs.gameoperators.PlayerTransactionResponse;
import aimlabs.gaming.rgs.gameoperators.Wallet;
import aimlabs.gaming.rgs.transactions.TransactionType;
import aimlabs.gaming.rgs.brandgames.BrandGame;
import aimlabs.gaming.rgs.brandgames.IBrandGameService;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import aimlabs.gaming.rgs.gameskins.IGameSkinService;
import aimlabs.gaming.rgs.gameskins.GameSkin;
import aimlabs.gaming.rgs.networks.INetworkService;
import aimlabs.gaming.rgs.networks.Network;
import aimlabs.gaming.rgs.settings.IGameSettingsService;
import aimlabs.gaming.rgs.tenant.TenantContextHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import aimlabs.gaming.rgs.gconnect.bfgames.controller.BFGamesConnectController;
import aimlabs.gaming.rgs.gconnect.bfgames.exceptions.SessionExpiredException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.Money;
import org.javamoney.moneta.function.MonetaryQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Qualifier("bf-games")
@Slf4j
@Getter
@Component
public class BFGamesPlayerServiceAdaptor implements PlayerAccountManagerFactory {

    @Value("${rgs.player.connector.bf-games.uid:bf-games-connector}")
    String connectorUid;

    private final RestClient.Builder restClientBuilder;

    @Autowired(required = false)
    private ICurrencyService currencyService;

    @Autowired(required = false)
    private INetworkService networkService;

    @Autowired(required = false)
    private IBrandGameService brandGameService;

    @Autowired(required = false)
    private IGameSettingsService gameSettingsService;

    @Autowired(required = false)
    private IGameSkinService gameSkinService;

    @Autowired
    public BFGamesPlayerServiceAdaptor(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public boolean supports(Connector connector) {
        return connectorUid.equals(connector.getUid())
                || connectorUid.equals(connector.getParentConnector());
    }

    @Override
    public PlayerAccountManager getInstance(Connector connector) {
        return new BFGamesPlayerAccountManager(connector);
    }

    public List<BFGamesConnectController.GameData> getGames(String currency) {
        CurrencyUnit currencyUnit = resolveCurrency(currency);

        Network network = networkService != null ? networkService.findOneByConnector(connectorUid) : null;
        if (network == null) {
            return List.of();
        }

        String networkUid = network.getUid();
        if (networkUid == null || networkUid.isBlank()) {
            return List.of();
        }

        List<BrandGame> brandGames = brandGameService != null
                ? brandGameService.findAllByNetwork(networkUid)
                : List.of();

        if (brandGames.isEmpty() || gameSettingsService == null || gameSkinService == null) {
            return List.of();
        }

        String tenant = TenantContextHolder.getTenant();

        List<BFGamesConnectController.GameData> out = new ArrayList<>();
        for (BrandGame brandGame : brandGames) {
            String brand = brandGame.getBrand();
            String gameId = brandGame.getGame();

            if (brand == null || brand.isBlank() || gameId == null || gameId.isBlank()) {
                continue;
            }

            GameSkin skin;
            try {
                skin = gameSkinService.findOneByUid(gameId);
            } catch (Exception e) {
                log.debug("bf-games getGames: failed to load GameSkin uid={} error={}", gameId, e.toString());
                continue;
            }
            if (skin == null) {
                continue;
            }

            Map<String, Object> settings;
            try {
                settings = gameSettingsService.findGameSettingsForCurrency(tenant, brand, gameId,
                        currencyUnit.getCurrencyCode());
            } catch (Exception e) {
                log.debug("bf-games getGames: failed settings brand={} game={} error={}", brand, gameId,
                        e.toString());
                settings = Map.of();
            }

            BFGamesConnectController.GameData dto = new BFGamesConnectController.GameData();
            dto.setId(skin.getUid());
            dto.setName(skin.getName());
            dto.setVersion(skin.getClientVersion());
            dto.setLines(skin.getPayLines());

            List<Integer> lineBetSteps = toMinorIntList(currencyUnit, settings.get("ladder"));
            dto.setLineBetSteps(lineBetSteps);

            Integer minTotalBet = toMinorInt(currencyUnit, firstNumber(settings.get("minMax"), 0));
            Integer maxTotalBet = toMinorInt(currencyUnit, firstNumber(settings.get("minMax"), 1));

            Integer defaultLineBet = toMinorInt(currencyUnit, asNumber(settings.get("defaultStake")));
            if (defaultLineBet == null && !lineBetSteps.isEmpty()) {
                defaultLineBet = lineBetSteps.get(0);
            }

            dto.setMinTotalBet(minTotalBet);
            dto.setMaxTotalBet(maxTotalBet);
            dto.setDefaultLineBet(defaultLineBet);

            Integer lines = dto.getLines();
            if (defaultLineBet != null) {
                dto.setDefaultTotalBet(lines != null && lines > 0 ? defaultLineBet * lines : defaultLineBet);
            }

            dto.setLicenses(toLicenseDetails(settings.get("licences"), settings.get("rtps")));
            out.add(dto);
        }

        return out;
    }

    private CurrencyUnit resolveCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "currency is missing");
        }

        if (currencyService != null) {
            CurrencyUnit cu = currencyService.getCurrency(currencyCode);
            if (cu != null) {
                return cu;
            }
        }

        return Monetary.getCurrency(currencyCode);
    }

    private static Number asNumber(Object o) {
        return (o instanceof Number n) ? n : null;
    }

    private static Number firstNumber(Object o, int index) {
        if (o == null) {
            return null;
        }
        if (o instanceof List<?> list) {
            if (index < 0 || index >= list.size()) {
                return null;
            }
            return asNumber(list.get(index));
        }
        if (o.getClass().isArray()) {
            Object[] arr = (Object[]) o;
            if (index < 0 || index >= arr.length) {
                return null;
            }
            return asNumber(arr[index]);
        }
        return null;
    }

    private static Integer toMinorInt(CurrencyUnit currencyUnit, Number major) {
        if (currencyUnit == null || major == null) {
            return null;
        }
        long minor = Money.of(BigDecimal.valueOf(major.doubleValue()), currencyUnit)
                .query(MonetaryQueries.convertMinorPart())
                .longValue();
        if (minor > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (minor < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) minor;
    }

    private static List<Integer> toMinorIntList(CurrencyUnit currencyUnit, Object ladder) {
        if (ladder == null) {
            return Collections.emptyList();
        }

        List<?> items;
        if (ladder instanceof List<?> list) {
            items = list;
        } else if (ladder.getClass().isArray()) {
            items = List.of((Object[]) ladder);
        } else {
            return Collections.emptyList();
        }

        List<Integer> out = new ArrayList<>(items.size());
        for (Object item : items) {
            Integer v = toMinorInt(currencyUnit, asNumber(item));
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private static List<BFGamesConnectController.GameData.LicenseDetails> toLicenseDetails(Object licencesObj,
            Object rtpsObj) {
        Double[] rtps = null;
        if (rtpsObj instanceof List<?> list) {
            rtps = list.stream().filter(Number.class::isInstance).map(Number.class::cast)
                    .map(n -> n.doubleValue()).toArray(Double[]::new);
        } else if (rtpsObj instanceof Number[] arr) {
            rtps = new Double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                rtps[i] = arr[i] == null ? null : arr[i].doubleValue();
            }
        } else if (rtpsObj instanceof double[] arr) {
            rtps = new Double[arr.length];
            for (int i = 0; i < arr.length; i++) {
                rtps[i] = arr[i];
            }
        }

        List<String> licences = new ArrayList<>();
        if (licencesObj instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    licences.add(String.valueOf(o));
                }
            }
        } else if (licencesObj instanceof String[] arr) {
            licences.addAll(List.of(arr));
        }

        if (licences.isEmpty()) {
            BFGamesConnectController.GameData.LicenseDetails ld = new BFGamesConnectController.GameData.LicenseDetails();
            if (rtps != null) {
                ld.setRtps(rtps);
            }
            return List.of(ld);
        }

        Double[] finalRtps = rtps;
        return licences.stream()
                .map(l -> new BFGamesConnectController.GameData.LicenseDetails(l, finalRtps))
                .toList();
    }

    private final class BFGamesPlayerAccountManager implements PlayerAccountManager {
        private final Connector connector;
        private final RestClient client;

        private BFGamesPlayerAccountManager(Connector connector) {
            this.connector = Objects.requireNonNull(connector, "connector");
            if (connector.getBaseUrl() == null || connector.getBaseUrl().isBlank()) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST,
                        "bf-games connector baseUrl is missing");
            }
            this.client = restClientBuilder.baseUrl(connector.getBaseUrl()).build();
        }

        @Override
        public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(request.getSessionToken());

            GenericRequest tokenAuthenticationRequest = new GenericRequest(
                    "authenticateToken",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            tokenAuthenticationRequest.setArgs(args);

            GenericResponse genericResponse = post(tokenAuthenticationRequest);
           

            if(genericResponse.getResult().getErrorcode()!=null && !"0".equals(genericResponse.getResult().getErrorcode())) {
                if ("2001" .equals(genericResponse.getResult().getErrorcode())) {
                    throw new BaseRuntimeException(SystemErrorCode.TOKEN_EXPIRED);
                }
                if ("2003" .equals(genericResponse.getResult().getErrorcode())) {
                    throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Player not logged in.");
                } else if ("2002" .equals(genericResponse.getResult().getErrorcode())) {
                    throw new BaseRuntimeException(SystemErrorCode.TOKEN_INVALID);
                } else {
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR);
                }
            }

            PlayerInitialiseResponse res = new PlayerInitialiseResponse();
            res.setPlayerId(genericResponse.getResult().player_id);
            res.setCurrency(genericResponse.getResult().currency);
            Money balance = Money.ofMinor(Monetary.getCurrency(genericResponse.getResult().currency),
                    genericResponse.getResult().balance);
            res.setTotalBalance(balance.getNumberStripped());
            res.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                    .total(balance.getNumberStripped()));
            res.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
            res.setExternalToken(request.getSessionToken());
            return res;
        }

        @Override
        public Wallet playerBalance(PlayerBalanceRequest request) {
            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(request.getToken());
            args.setCurrency(request.getCurrency());
            args.setGame_ref(request.getGameId());
            args.setTimestamp(System.currentTimeMillis());

            GenericRequest getBalanceRequest = new GenericRequest(
                    "getBalance",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            getBalanceRequest.setArgs(args);

            GenericResponse genericResponse = post(getBalanceRequest);
            //ensureOk(genericResponse, false);

            CurrencyUnit cu = Monetary.getCurrency(genericResponse.getResult().currency);
            Money balance = Money.ofMinor(cu, genericResponse.getResult().balance);

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setCash(new Balance().amount(balance.getNumberStripped()).onHold(BigDecimal.ZERO)
                    .total(balance.getNumberStripped()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));
            wallet.setTotalBalance(balance.getNumberStripped());
            return wallet;
        }

        @Override
        public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
            TransactionType type = request.getRequestType();
            if (type == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "requestType is null");
            }

            return switch (type) {
                case ROLLBACK -> rollback(request);
                case CREDIT, CLOSED -> creditOrClosed(request);
                case DEBIT -> debit(request);
                case DEBIT_CREDIT -> debitCredit(request);
            };
        }

        @Override
        public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
            TxnResult txn = processTxn(
                    request,
                    request.getTxnId(),
                    0d,
                    TransactionType.ROLLBACK,
                    true);
            return toTxnResponse(request, List.of(txn));
        }

        @Override
        public void closeSession(PlayerBalanceRequest request) {
            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setStatus("CLOSE");
            args.setToken(request.getToken());

            GenericRequest terminateRequest = new GenericRequest(
                    "terminate",
                    new GenericRequest.Mirror(UUID.randomUUID().toString()));
            terminateRequest.setArgs(args);

            post(terminateRequest);
        }

        private PlayerTransactionResponse creditOrClosed(PlayerTransactionRequest request) {
            Double credit = request.getCredit();
            if (request.getRequestType() == TransactionType.CLOSED || credit == null) {
                credit = 0d;
            }
            TxnResult txn = processTxn(
                    request,
                    request.getTxnId(),
                    credit,
                    TransactionType.CREDIT,
                    true);
            return toTxnResponse(request, List.of(txn));
        }

        private PlayerTransactionResponse debit(PlayerTransactionRequest request) {
            if (request.getDebit() == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
            }
            TxnResult txn = processTxn(
                    request,
                    request.getTxnId(),
                    request.getDebit(),
                    TransactionType.DEBIT,
                    false);
            return toTxnResponse(request, List.of(txn));
        }

        private PlayerTransactionResponse debitCredit(PlayerTransactionRequest request) {
            if (request.getDebit() == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Debit amount is null");
            }
            if (request.getCredit() == null) {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "Credit amount is null");
            }

            TxnResult debit = processTxn(
                    request,
                    request.getTxnId() + "_0",
                    request.getDebit(),
                    TransactionType.DEBIT,
                    false);
            TxnResult credit = processTxn(
                    request,
                    request.getTxnId() + "_1",
                    request.getCredit(),
                    TransactionType.CREDIT,
                    true);
            return toTxnResponse(request, List.of(debit, credit));
        }

        private PlayerTransactionResponse toTxnResponse(PlayerTransactionRequest request, List<TxnResult> results) {
            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());
            long lastBalanceMinor = results.isEmpty()
                    ? 0
                    : results.get(results.size() - 1).response.getResult().balance;
            Money balance = Money.ofMinor(cu, lastBalanceMinor);

            Wallet wallet = new Wallet();
            wallet.setCurrency(cu.getCurrencyCode());
            wallet.setTotalBalance(balance.getNumberStripped());
            wallet.setCash(new Balance().amount(wallet.getTotalBalance()).onHold(BigDecimal.ZERO)
                    .total(wallet.getTotalBalance()));
            wallet.setBonus(new Balance().amount(BigDecimal.ZERO).onHold(BigDecimal.ZERO).total(BigDecimal.ZERO));

            Map<String, Object> processed = new HashMap<>();
            for (TxnResult result : results) {
                String processedId = result.response.getResult().transaction_id;
                if (processedId != null) {
                    processed.put(result.requestTxnId, processedId);
                }
            }

            PlayerTransactionResponse response = new PlayerTransactionResponse();
            response.setTxnId(request.getTxnId());
            response.setWallet(wallet);
            response.setProcessedTxnIds(processed);
            return response;
        }

        private TxnResult processTxn(
                PlayerTransactionRequest request,
                String txnId,
                Double amount,
                TransactionType type,
                boolean roundClosed) {

            CurrencyUnit cu = Monetary.getCurrency(request.getCurrency());
            long minorAmount = Money.of(amount != null ? amount : 0D, cu).query(MonetaryQueries.convertMinorPart());

            GenericRequest.Args args = new GenericRequest.Args();
            addCallerIdAndPassword(args);
            args.setToken(request.getToken());
            args.setCurrency(cu.getCurrencyCode());
            args.setGame_ref(request.getGameId());
            if (request.getGameVersion() != null) {
                args.setGame_ver(request.getGameVersion());
            }
            args.setRound_id(request.getGameRoundId());
            args.setAction_id(txnId);
            args.setOffline(false);

            GenericRequest playRequest;
            if (type == TransactionType.CREDIT) {
                playRequest = new GenericRequest("deposit", new GenericRequest.Mirror(UUID.randomUUID().toString()));
                args.setAmount(minorAmount);
                args.setEnd_round(roundClosed);
            } else if (type == TransactionType.DEBIT) {
                playRequest = new GenericRequest("withdraw", new GenericRequest.Mirror(UUID.randomUUID().toString()));
                args.setAmount(minorAmount);
            } else if (type == TransactionType.ROLLBACK) {
                playRequest = new GenericRequest("rollback", new GenericRequest.Mirror(UUID.randomUUID().toString()));
                args.setWithdraw_action_id(request.getOrgTxnUid());
                if (request.getOrgTxnAmount() != null) {
                    args.setWithdraw_amount(
                            Money.of(request.getOrgTxnAmount(), cu).query(MonetaryQueries.convertMinorPart()));
                }
            } else {
                throw new BaseRuntimeException(SystemErrorCode.INVALID_REQUEST, "unsupported transaction " + type);
            }

            playRequest.setArgs(args);

            GenericResponse response;
            try {
                response = post(playRequest);
            } catch (RuntimeException e) {
                if (type == TransactionType.DEBIT) {
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, e);
                }
                throw e;
            }


            String errorcode = response.getResult().errorcode;
            if(errorcode==null){
                return new TxnResult(txnId, response);
            }
        
            if("1000".equals(errorcode)){
                if(type == TransactionType.DEBIT)
                    throw new BaseRuntimeException(SystemErrorCode.ROLLBACK_GAME_ROUND, response.result.toString());

                // This should not occur ideally because error 1000 will not be returned for Rollback request.
                else if(type == TransactionType.ROLLBACK)
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Rollback gameRound "+request.getGameRoundId()+" failed!");
                else// This should not occur ideally because operator send 1000 errocode only for debit requests.
                    throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "Cannot handle 1000 errorCode for txnType " + type);
            }
            else if(errorcode.startsWith("200")
                    && !playRequest.getArgs().isOffline() //try offline deposit only if it is not an offline deposit request.
                    && (type ==TransactionType.CREDIT || type== TransactionType.ROLLBACK )) {
                throw new SessionExpiredException();//retry with offline deposit / rollback
            }
            else if(errorcode.equals("4000") &&  type == TransactionType.DEBIT){
                throw new BaseRuntimeException(SystemErrorCode.INSUFFICIENT_BALANCE, errorcode);
            }
            //else if(errorcode.startsWith("300") || errorcode.startsWith("400"))
            else if(errorcode.startsWith("300") || errorcode.startsWith("400")){
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, errorcode);
            }
            else{
                throw new BaseRuntimeException(SystemErrorCode.GENERAL_API_ERROR, "request failed with errorcode "+errorcode);
            }
        }

        private GenericResponse post(GenericRequest request) {
            GenericResponse response = client
                    .post()
                    .uri("/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenericResponse.class);

            if (response == null) {
                throw new BaseRuntimeException(SystemErrorCode.EMPTY_RESPONSE, "bf-games returned empty response");
            }
            return response;
        }

        private void addCallerIdAndPassword(GenericRequest.Args args) {
            args.setCaller_id(getUser());
            args.setCaller_password(getPassword());
        }

        private String getPassword() {
            return connector.getSettings().getOrDefault("password", "").toString();
        }

        private String getUser() {
            return connector.getSettings().getOrDefault("user", "").toString();
        }
    }

    @Data
    @AllArgsConstructor
    private static final class TxnResult {
        String requestTxnId;
        GenericResponse response;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class GenericRequest {
        String methodname;
        Mirror mirror;
        String version = "1.0";
        String type = "jsonwsp/request";

        Args args;

        GenericRequest(String methodname, Mirror mirror) {
            this.methodname = methodname;
            this.mirror = mirror;
        }

        @Data
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class Args {
            String status;
            String caller_id;
            String caller_password;
            String token;
            String operator_id;
            long timestamp;
            String currency;
            String game_ref;
            String game_ver = "1.0.0";
            long amount;
            long withdraw_amount;
            String withdraw_action_id;
            String round_id;
            boolean offline;
            boolean end_round;
            String action_id;

            public Args setOffline(boolean offline) {
                this.offline = offline;
                return this;
            }
        }

        @Data
        @AllArgsConstructor
        @JsonInclude(JsonInclude.Include.NON_NULL)
        static class Mirror {
            String id;
        }
    }

    @Data
    static class GenericResponse {
        String methodname;
        Reflection reflection;
        String servicenumber;
        String servicename;
        String version = "1.0";
        String type = "jsonwsp/request";

        Result result;

        @Data
        static class Reflection {
            String id;
        }

        @Data
        static class Result {
            String errorcode;
            String currency;
            String token;
            long balance;
            String transaction_id;
            String player_id;
            String nickname;
        }
    }
}
