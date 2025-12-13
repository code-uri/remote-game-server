// java
package aimlabs.gaming.rgs.gameoperators;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.connectors.IConnectorService;
import aimlabs.gaming.rgs.core.event.EntityUpdateEvent;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamesessions.GameSession;

import aimlabs.gaming.rgs.gamesessions.GameSessionContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Data
@Slf4j
@Component
@Primary
public class PlayerAccountManagerDelegate implements PlayerAccountManager {

    public static final String CONNECTOR_FROM_CONTEXT = "PAM_CONNECTOR";
    public static final String TENANT_FROM_CONTEXT = "TENANT";
    public static final String SPLIT_CHAR = "/";
    public static final String DEMO_SESSION_PREFIX = "demo";

    // Cache for PlayerAccountManager instances
    private final Map<String, PlayerAccountManager> adapterCache = new ConcurrentHashMap<>();

    private final Map<String, PlayerAccountManagerFactory> playerAccountManagerFactories;
    private final IConnectorService connectorService;
    private final DemoPlayerServiceAdapter demoPlayerServiceAdapter;

    @Autowired
    public PlayerAccountManagerDelegate(
            Map<String, PlayerAccountManagerFactory> playerAccountManagerFactories,
            IConnectorService connectorService,
            DemoPlayerServiceAdapter demoPlayerServiceAdapter) {
        this.playerAccountManagerFactories = playerAccountManagerFactories;
        this.connectorService = connectorService;
        this.demoPlayerServiceAdapter = demoPlayerServiceAdapter;
    }

    private static boolean isDemoSession(String token) {
        return token != null && token.toLowerCase().startsWith(DEMO_SESSION_PREFIX);
    }

    /**
     * Listen to Connector update events and invalidate cache.
     */
    @EventListener
    public void handleConnectorUpdateEvent(EntityUpdateEvent event) {
        if (event.getSource() instanceof Connector connector) {
            final String tenant = connector.getTenant();
            final String connectorUid = connector.getUid();

            if (tenant == null || connectorUid == null) {
                log.warn("Received Connector update with null tenant or uid: {}", connector);
                return;
            }

            final String cacheKey = tenant + SPLIT_CHAR + connectorUid;
            PlayerAccountManager removed = adapterCache.remove(cacheKey);

            if (removed != null) {
                log.info("Invalidated PlayerAccountManager cache entry for key: {}", cacheKey);
            } else {
                log.debug("No cache entry found for key: {}", cacheKey);
            }
        }
    }

    // --- public API: now synchronous ---

    @Override
    public PlayerInitialiseResponse playerInitialise(PlayerInitialiseRequest request) {
        PlayerAccountManager adapter = resolveAdapter(request.getTenant(), request.getSessionToken());
        return adapter.playerInitialise(request);
    }

    @Override
    public Wallet playerBalance(PlayerBalanceRequest request) {
        PlayerAccountManager adapter = resolveAdapter(request.getTenant(), request.getToken());
        return adapter.playerBalance(request);
    }

    @Override
    public PlayerTransactionResponse playerTransaction(PlayerTransactionRequest request) {
        PlayerAccountManager adapter = resolveAdapter(request.getTenant(), request.getToken());
        return adapter.playerTransaction(request);
    }

    @Override
    public PlayerTransactionResponse rollback(PlayerTransactionRequest request) {
        PlayerAccountManager adapter = resolveAdapter(request.getTenant(), request.getToken());
        return adapter.rollback(request);
    }

    @Override
    public void keepSessionAlive(GameSession session) {
        PlayerAccountManager adapter = resolveAdapter(session.getTenant(), session.getUid());
        adapter.keepSessionAlive(session);
    }

    // --- internal helpers ---

    private PlayerAccountManager resolveAdapter(String tenant, String token) {
        if (isDemoSession(token)) {
            return demoPlayerServiceAdapter;
        }

        String connectorUid = resolveConnectorUidFromToken(token);
        if (connectorUid == null) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR,
                    "Cannot resolve connector from token for tenant=" + tenant);
        }

        return getAdapter(tenant, connectorUid);
    }

    private String resolveConnectorUidFromToken(String token) {
        if(GameSessionContext.current()!=null) {;
            String connectorUid = (String) GameSessionContext.current().getPamConnector();
            if (connectorUid != null) {
                return connectorUid;
            }
        }
        return null;
    }

    private PlayerAccountManager getAdapter(String tenant, String connectorUid) {
        String cacheKey = tenant + SPLIT_CHAR + connectorUid;

        return adapterCache.computeIfAbsent(cacheKey, key -> {
            Connector connector = connectorService.findOneByTenantAndConnector(tenant, connectorUid);
            if (connector == null) {
                throw new BaseRuntimeException(SystemErrorCode.CONNECTOR_NOT_FOUND,
                        "connector " + connectorUid + " not found");
            }

            return playerAccountManagerFactories.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().supports(connector))
                    .findFirst()
                    .orElseThrow(() -> new BaseRuntimeException(
                            SystemErrorCode.INVALID_BRAND,
                            "No factory supports connector: " + connectorUid))
                    .getValue()
                    .getInstance(connector);
        });
    }

    // generic delegate if needed in future (now synchronous)
    private <T> T delegateToAdapter(
            String tenant,
            String token,
            Function<PlayerAccountManager, T> action) {

        PlayerAccountManager adapter = resolveAdapter(tenant, token);
        try {
            return action.apply(adapter);
        } catch (BaseRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseRuntimeException(SystemErrorCode.INTERNAL_ERROR, e);
        }
    }
}