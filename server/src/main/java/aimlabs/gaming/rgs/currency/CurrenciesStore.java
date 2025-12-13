package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.MongoEntityStore;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import lombok.Data;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
public class CurrenciesStore extends MongoEntityStore<CurrencyDocument> {
    String name;
    List<String> permissions;

    public CurrencyDocument findOneByCode(String currency) {
        return this.getTemplate().findOne(Query.query(Criteria.where("code").is(currency)
                .and("deleted").is(false).and("tenant").is(TenantContextHolder.getTenant())), CurrencyDocument.class);
    }

    public List<CurrencyDocument> findAllISOCurrencies() {
        return this.getTemplate().find(Query.query(Criteria.where("deleted").is(false)
                .and("iso").is(true).and("tenant").is(TenantContextHolder.getTenant())), CurrencyDocument.class);
    }

    public String getName() {
        return this.name;
    }

    public List<String> getPermissions() {
        return this.permissions;
    }

    public CurrenciesStore setName(final String name) {
        this.name = name;
        return this;
    }

    public CurrenciesStore setPermissions(final List<String> permissions) {
        this.permissions = permissions;
        return this;
    }
}
