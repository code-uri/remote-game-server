package aimlabs.gaming.rgs.connectors;


import aimlabs.gaming.rgs.core.AbstractEntityService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Data
@Service
public class ConnectorService extends AbstractEntityService<Connector, ConnectorDocument> implements IConnectorService {

    @Autowired
    private ConnectorStore store;

    @Autowired
    private ConnectorMapper mapper;

    @Cacheable
    public Connector findOneByTenantAndConnector(String tenant, String connector) {
        return getMapper().asDto(getStore().findOneByTenantAndConnector(tenant, connector));
    }

/*    @Override
    public Mono<Connector> findOneByTenant(String tenant) {
        return getStore().findOneByTenant(tenant)
                .map(getMapper()::asDto);
    }*/


    @Override
    //@CacheBust
    public Connector update(Connector connector) {
        return super.update(connector);
    }

    @Override
    //@CacheBust
    public Connector updatePartial(String uid, Map<String, Object> values) {
        return super.updatePartial(uid, values);
    }

}
