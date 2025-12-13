package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.security.SecuredEndpoint;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/countries")
@SecuredEndpoint
public class CountriesController {

    static class Country {
        String code;

        public Country(String s) {
            this.code = s;
        }
    }

    @GetMapping
    public List<Country> findAll() {
        return Arrays.stream(Locale.getISOCountries()).map(Country::new).toList();
    }
}
