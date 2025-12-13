package aimlabs.gaming.rgs.admin.controller;

import aimlabs.gaming.rgs.security.SecuredEndpoint;
import aimlabs.gaming.rgs.brands.Brand;
import aimlabs.gaming.rgs.brands.BrandService;
import aimlabs.gaming.rgs.brandgames.BrandGameService;
import aimlabs.gaming.rgs.core.AbstractEntityCurdController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Data
@Slf4j
@RestController
@RequestMapping("/admin/brands")
@SecuredEndpoint
public class BrandController extends AbstractEntityCurdController<Brand> {

    @Autowired
    private BrandService service;

    @Autowired
    private BrandGameService brandGameService;
}
