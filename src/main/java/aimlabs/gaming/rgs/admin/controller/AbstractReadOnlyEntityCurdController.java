package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;

import java.util.Map;

// Note: This class has been simplified to remove reactive types
@SuppressWarnings("unused")
public abstract class AbstractReadOnlyEntityCurdController<T extends BaseDto> extends AbstractEntityCurdController<T> {

    public T createEntity(T dto) {
        throw new UnsupportedOperationException("Read only entity repository");
    }

    public T updateEntity(T dto, String uid) {
        throw new UnsupportedOperationException("Read only entity repository");
    }

    public T updatePartial(String uid, Map<String, Object> values) {
        throw new UnsupportedOperationException("Read only entity repository");
    }

    public void delete(String uid) {
        throw new UnsupportedOperationException("Read only entity repository");
    }

    /*
     * public Mono<T> save(T dto) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * public Mono<T> update(String uid, T dto) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * public Mono<T> updatePartial(String uid, Map<String, Object> values) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * public Mono<T> saveEntity(T dto) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * public Mono<T> updateEntity(String uid, T dto) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * 
     * 
     * 
     * public Flux<EntityNote> getNotes(String id) {
     * return Flux.error(new UnsupportedOperationException());
     * }
     * 
     * 
     * public Mono<EntityNote> addNotes(String id, Map<String, String> data) {
     * return Mono.error(new UnsupportedOperationException());
     * }
     */
}
