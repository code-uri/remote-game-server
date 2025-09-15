package aimlabs.gaming.rgs.engine.verification;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerReport extends BaseDto {
     String refId;
     List<GameEngineVerificationReport> reports;
     String triggered;
     String serverStatus;
}