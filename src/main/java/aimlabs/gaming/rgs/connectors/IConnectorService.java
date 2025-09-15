package aimlabs.gaming.rgs.connectors;

import aimlabs.gaming.rgs.core.IEntityService;

public interface IConnectorService extends IEntityService<Connector> {

    //Mono<Connector> findOneByTenant(String tenant);

    Connector findOneByTenantAndConnector(String tenant, String connector);

}