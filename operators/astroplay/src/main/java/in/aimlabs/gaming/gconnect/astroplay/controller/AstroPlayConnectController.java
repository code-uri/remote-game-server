package in.aimlabs.gaming.gconnect.astroplay.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/astroplay")
public class AstroPlayConnectController {

    @Value("${rgs.player.connector.astroplay.partner:astroplay}")
    String partner;
}
