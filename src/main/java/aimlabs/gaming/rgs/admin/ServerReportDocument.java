package aimlabs.gaming.rgs.admin;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import aimlabs.gaming.rgs.core.documents.EntityDocument;
import aimlabs.gaming.rgs.engine.verification.GameEngineVerificationReport;

import java.util.List;

@Data
@Document(collection = "ServerReports")
public class ServerReportDocument extends EntityDocument {

    String refId;
    List<GameEngineVerificationReport> reports;
    String triggered;
    String serverStatus;
}
