// java
package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.AbstractEntityService;
import aimlabs.gaming.rgs.games.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.javamoney.moneta.CurrencyUnitBuilder;
import org.javamoney.moneta.spi.ConfigurableCurrencyUnitProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;

import javax.money.CurrencyContextBuilder;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Slf4j
@Service
public class CurrencyServiceImpl extends AbstractEntityService<Currency, CurrencyDocument> implements ICurrencyService {
    @Autowired
    CurrenciesStore store;
    @Autowired
    CurrencyMapper mapper;

    @Autowired
    @Qualifier("tenants")
    private Set<String> tenants;

    public List<String> getAllCurrencies() {
        return this.store.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .map(CurrencyDocument::getCode)
                .collect(Collectors.toList());
    }

    public List<Currency> findAllISOCurrencies() {
        return this.store.findAllISOCurrencies().stream()
                .map(this.mapper::asDto)
                .collect(Collectors.toList());
    }

    public List<String> getAllCurrencies(String language, String country, String variant) {
        return this.store.findAll(Sort.by(Sort.Order.asc("id"))).stream()
                .map(CurrencyDocument::getCode)
                .collect(Collectors.toList());
    }

    public CurrencyUnit getCurrency(String currency) {
        if (!Monetary.isCurrencyAvailable(currency, new String[0])) {

            Currency as = getMapper().asDto(this.store.findOneByCode(currency));

            if (!Monetary.isCurrencyAvailable(currency, new String[0])) {
                registerDBCurrency(as);
            }

            return Monetary.getCurrency(currency, new String[0]);
        } else
            return Monetary.getCurrency(currency, new String[0]);
    }

    //@CacheBust
    public Currency update(Currency currency) {
        return super.update(currency);
    }

    @EventListener
    public void handleContextRefreshEvent(RegisterCurrenciesEvent registerCurrenciesEvent) {
        log.info("Register Currencies Event received.");
        Collection<CurrencyUnit> allCurrencies = Monetary.getCurrencies(new String[0]);
        Map<String, CurrencyUnit> registered = allCurrencies.stream()
                .collect(Collectors.toMap(CurrencyUnit::getCurrencyCode, cu -> cu));
        Collection<CurrencyUnit> registerdCurrencies = Monetary.getCurrencies();

        for (String tenant : tenants) {
            ScopedValue.where(TenantContextHolder.getScopedValue(), tenant).run(() -> {
                Collection<CurrencyDocument> currencyDocuments = this.store.findAll(Sort.by(Order.asc("id")));

                List<String> dbCurrencies = currencyDocuments.stream()
                        .map(currency -> {
                            Currency currencyDto = this.mapper.asDto(currency);
                            if (!registered.containsKey(currency.getCode())) {
                                registerDBCurrency(currencyDto);
                            } else if (!Monetary.isCurrencyAvailable(currency.getCode(), new String[0])) {
                                CurrencyUnit cu = registered.get(currency.getCode());
                                List<Map.Entry<String, Object>> updatesAvailable = currency.getData().entrySet().stream()
                                        .filter(entry -> cu.getContext().get(entry.getKey(), entry.getValue().getClass()) == null)
                                        .collect(Collectors.toList());
                                if (currency.getFractionalDigits() != cu.getDefaultFractionDigits() || !updatesAvailable.isEmpty()) {
                                    registerCurrency(currencyDto, cu);
                                }
                            }

                            return currency.getCode();
                        })
                        .toList();

                List<CurrencyDocument> newList = registerdCurrencies.stream()
                        .filter(currencyUnit -> !dbCurrencies.contains(currencyUnit.getCurrencyCode()))
                        .map(currencyUnit -> {
                            CurrencyDocument register = new CurrencyDocument();
                            register.setType("regular");
                            register.setCode(currencyUnit.getCurrencyCode());
                            register.setTenant(tenant);
                            register.setNumericCode(currencyUnit.getNumericCode());
                            register.setName(currencyUnit.getCurrencyCode());
                            register.setFractionalDigits(currencyUnit.getDefaultFractionDigits());
                            return register;
                        })
                        .toList();

                this.store.saveAll(newList);
            });
        }
    }

    private static void registerDBCurrency(Currency currency) {
        CurrencyUnitBuilder.of(currency.getCode(), "DBCurrencyProvider")
                .setDefaultFractionDigits(currency.getFractionalDigits())
                .build(true);
    }

    private static void registerCurrency(Currency currency, CurrencyUnit cu) {
        ConfigurableCurrencyUnitProvider.removeCurrencyUnit(currency.getCode());
        CurrencyContextBuilder currencyContextBuilder = CurrencyContextBuilder.of(cu.getContext().getProviderName());
        if (!currency.getData().isEmpty()) {
            Map<String, Object> var10000 = currency.getData();
            Objects.requireNonNull(currencyContextBuilder);
            var10000.forEach((k, v) -> currencyContextBuilder.set(k, v));
        }

        CurrencyUnitBuilder.of(currency.getCode(), currencyContextBuilder.build())
                .setDefaultFractionDigits(currency.getFractionalDigits())
                .build(true);
    }
}