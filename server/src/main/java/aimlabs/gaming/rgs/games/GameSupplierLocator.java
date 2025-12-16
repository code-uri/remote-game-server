package aimlabs.gaming.rgs.games;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.connectors.IConnectorService;
import aimlabs.gaming.rgs.core.exceptions.BaseRuntimeException;
import aimlabs.gaming.rgs.core.exceptions.SystemErrorCode;
import aimlabs.gaming.rgs.gamesupplier.GameSupplierServiceFactory;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;

@Service
public class GameSupplierLocator {

    private List<GameSupplierServiceFactory> gameSupplierFactories;

    GameSupplierLocator(List<GameSupplierServiceFactory> gameSupplierFactories) {
        this.gameSupplierFactories = gameSupplierFactories;
    }

    @Autowired
    private IConnectorService connectorService;

    public IGameSupplierService getSupplier(String connectorUid) {

        Connector connector;
        if (connectorUid == null || "local-connector".equals(connectorUid)) {
            connector = new Connector();
        } else {
            connector = connectorService.findOneByUid(connectorUid);
        }

        return gameSupplierFactories.stream()
                .filter(gameSupplierServiceFactory -> gameSupplierServiceFactory.supports(connector)).findFirst()
                .orElseThrow(() -> new BaseRuntimeException(SystemErrorCode.NOT_FOUND))
                .getInstance(connector);
    }
}
