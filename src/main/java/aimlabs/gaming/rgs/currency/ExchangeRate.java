package aimlabs.gaming.rgs.currency;

import aimlabs.gaming.rgs.core.entity.BaseDto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ExchangeRate extends BaseDto {

    LocalDate date;

    String from;

    String to;

    String exchangeRate;
}
