package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.IEntityService;

public interface IExchangeRateService extends IEntityService<ExchangeRate> {

   public Double getRate(String from, String to);

}