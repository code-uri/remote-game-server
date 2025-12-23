package aimlabs.gaming.rgs.gameoperators;


import aimlabs.gaming.rgs.connectors.Connector;

public interface PlayerAccountManagerFactory {

    boolean supports(Connector connector);

    PlayerAccountManager getInstance(Connector connector);
}
