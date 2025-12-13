package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import aimlabs.gaming.rgs.currency.Currency;
import aimlabs.gaming.rgs.currency.ICurrencyService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/currencies")
public class CurrenciesController extends AbstractEntityCurdController<Currency> {

    @Autowired
    private ICurrencyService service;
}
