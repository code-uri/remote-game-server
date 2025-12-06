package aimlabs.gaming.rgs.admin.controller;

import lombok.Data;

import aimlabs.gaming.rgs.currency.ExchangeRate;
import aimlabs.gaming.rgs.currency.IExchangeRateService;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/exchange-rates")
@Data
public class ExchangeRateController extends AbstractEntityCurdController<ExchangeRate> {

    @Autowired
    IExchangeRateService service;

}