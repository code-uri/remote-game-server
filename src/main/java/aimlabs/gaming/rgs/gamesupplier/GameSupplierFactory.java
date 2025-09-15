package aimlabs.gaming.rgs.gamesupplier;


import aimlabs.gaming.rgs.connectors.Connector;

public interface GameSupplierFactory {

    boolean supports(Connector connector);

    IGameSupplierService getInstance(Connector connector);
}
