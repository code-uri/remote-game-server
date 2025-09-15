package aimlabs.gaming.rgs.networks;

import aimlabs.gaming.rgs.core.IEntityService;

public interface INetworkService extends IEntityService<Network> {

    Network findOneByConnector(String connectorUid);

    Network findOneByClientId(String clientId);

}
