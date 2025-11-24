// java
package aimlabs.gaming.rgs.brands;


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
public class BrandService extends AbstractEntityService<Brand, BrandDocument> implements IBrandService {

    @Autowired
    private BrandStore store;

    @Autowired
    private BrandMapper mapper;

    @Cacheable
    public Brand findOneByTenantAndBrand(String tenant, String brand) {
        BrandDocument doc = this.getStore()
                .findOneByTenantAndBrand(tenant, brand.toLowerCase());
        return doc != null ? getMapper().asDto(doc) : null;
    }

    @Cacheable
    public Brand findOneByUidAndNetwork(String uid, String network) {
        BrandDocument doc = this.getStore()
                .findOneByUidAndNetwork(uid.toLowerCase(), network);
        return doc != null ? getMapper().asDto(doc) : null;
    }

    @Override
    //@CacheBust
    public Brand update(Brand obj) {
        return super.update(obj);
    }

    @Override
    //@CacheBust
    public Brand updatePartial(String uid, Map<String, Object> values) {
        return super.updatePartial(uid, values);
    }
}