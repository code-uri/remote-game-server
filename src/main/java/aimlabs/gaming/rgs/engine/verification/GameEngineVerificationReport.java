package aimlabs.gaming.rgs.engine.verification;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class GameEngineVerificationReport {

    String fileName;
    List<String> gameConfigurations;
    String path;
    String expected;
    String actual;
    String remarks;
    String status;
}