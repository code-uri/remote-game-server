package aimlabs.gaming.rgs.core.utils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogUtil {

    public static <T extends Object> T logEntity(T entity) {
        log.info("Entity: {}", entity);
        return entity;
    }
}
