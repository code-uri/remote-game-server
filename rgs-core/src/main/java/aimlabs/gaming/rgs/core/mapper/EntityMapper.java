package aimlabs.gaming.rgs.core.mapper;

import java.io.Serializable;

public interface EntityMapper<T extends Serializable, E extends Serializable> {

    T asDto(E entity);

    E asEntity(T dto);
}
