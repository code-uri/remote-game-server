package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.IEntityService;

import javax.money.CurrencyUnit;
import java.util.Collection;

public interface ICurrencyService extends IEntityService<Currency> {
    Collection<String> getAllCurrencies();

    Collection<Currency> findAllISOCurrencies();

    Collection<String> getAllCurrencies(String language, String country, String variant);

    CurrencyUnit getCurrency(String currency);
}
