package aimlabs.gaming.rgs.games;


import aimlabs.gaming.rgs.connectors.Connector;
import aimlabs.gaming.rgs.gamesupplier.IGameSupplierService;

public interface GameSupplierServiceFactory {

    boolean supports(Connector connector);

    IGameSupplierService getInstance(Connector connector);
}
