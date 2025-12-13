package aimlabs.gaming.rgs.brands;

import aimlabs.gaming.rgs.core.IEntityService;

import java.util.Map;

public interface IBrandService extends IEntityService<Brand> {
    Brand findOneByTenantAndBrand(String tenant, String brand);
    Brand findOneByUidAndNetwork(String uid, String network);
    Brand update(Brand obj);
    Brand updatePartial(String uid, Map<String, Object> values);
}