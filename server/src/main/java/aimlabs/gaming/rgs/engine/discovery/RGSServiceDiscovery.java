package aimlabs.gaming.rgs.engine.discovery;

import aimlabs.gaming.rgs.engine.verification.GameEngineVerificationReport;
import aimlabs.gaming.rgs.engine.verification.ServerReport;
import in.aimlabs.gaming.engine.api.model.GameEngineRequest;
import in.aimlabs.gaming.engine.api.model.GameEngineResponse;
import in.aimlabs.gaming.engine.api.module.GameEngineModule;
import in.aimlabs.gaming.engine.api.service.GameEngine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@Data
public class RGSServiceDiscovery  implements ApplicationListener<GameEnginesLoadedEvent> {

    @Autowired
    RGSEngineProperties rgsEngineProperties;

    @Autowired
    MongoTemplate mongoTemplate;

    private final Map<String, GameEngine<GameEngineRequest, GameEngineResponse>>
            gameEngineServices = new ConcurrentHashMap<>();


    public List<GameEngineVerificationReport>
            gameEngineVerificationReports = new LinkedList<>();

    private final Map<String, GameEngineModule> gameEngineModuleMap = new ConcurrentHashMap<>();

    public void addEngineService(String gameConfiguration, GameEngine<GameEngineRequest, GameEngineResponse> engineService) {
/*        if (gameEngineServices.containsKey(gameConfiguration))
            throw new IllegalArgumentException("Game configuration " + gameConfiguration + ", already mapped to engine "
                    + gameEngineServices.get(gameConfiguration).getClass().getCanonicalName());*/
        gameEngineServices.put(gameConfiguration, engineService);
    }

    public GameEngine<GameEngineRequest, GameEngineResponse> getEngineService(String gameConfiguration) {
        return gameEngineServices.get(gameConfiguration);
    }

    public void addGameEngineModule(String gameConfiguration, GameEngineModule module) {
        if (!gameEngineModuleMap.containsKey(gameConfiguration)) {
            gameEngineModuleMap.put(gameConfiguration, module);
        }
    }

    public Set<String> supportedGameConfigurations() {
        return this.gameEngineServices.keySet();
    }

    public GameEngineModule getRegisteredGameEngineModule(String gameConfiguration) {
        return this.gameEngineModuleMap.get(gameConfiguration);
    }

    static MessageDigest sha256Digest;
    static {
        try {
            sha256Digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }



    private static String checksum(File file)
            throws IOException
    {
        // Get file input stream for reading the file
        // content
        FileInputStream fis = new FileInputStream(file);

        // Create byte array to read data in chunks
        byte[] byteArray = new byte[1024];
        int bytesCount = 0;

        // read the data from file and update that data in
        // the message digest
        while ((bytesCount = fis.read(byteArray)) != -1)
        {
            sha256Digest.update(byteArray, 0, bytesCount);
        };

        // close the input stream
        fis.close();

        // store the bytes returned by the digest() method
        byte[] bytes = sha256Digest.digest();

        sha256Digest.reset();

        // this array of bytes has bytes in decimal format
        // so we need to convert it into hexadecimal format

        // for this we create an object of StringBuilder
        // since it allows us to update the string i.e. its
        // mutable
        StringBuilder sb = new StringBuilder();

        // loop through the bytes array
        for (int i = 0; i < bytes.length; i++) {

            // the following line converts the decimal into
            // hexadecimal format and appends that to the
            // StringBuilder object
            sb.append(Integer
                    .toString((bytes[i] & 0xff) + 0x100, 16)
                    .substring(1));
        }

        // finally, we return the complete hash
        return sb.toString();
    }

    public boolean test(File toFile, String expected, List<String> gameConfigurations) {
        if(!getRgsEngineProperties().isEnableVerification())
            return true;

        //String expected = getRgsEngineProperties().getHashes().get(toFile.getName().replace("-"+buildVersion + "-all.jar", ""));
        String actual = null;
        boolean valid = false;
        GameEngineVerificationReport report =null;
        try {
            actual = checksum(toFile);
            valid =  actual.equals(expected);
            if(!valid){
                report = new GameEngineVerificationReport( toFile.getName(), gameConfigurations, toFile.getAbsolutePath(), expected,actual,toFile.getName()+" sha256 verification failed!. Expected  hash "+expected+", found "+actual, "FAILED");
            }else{
                report = new GameEngineVerificationReport(toFile.getName(), gameConfigurations, toFile.getAbsolutePath(), expected, actual,toFile.getName()+" sha256:"+actual +" Verified.", "MATCHED");
            }
        } catch (Exception e) {
            report = new GameEngineVerificationReport( toFile.getName(),gameConfigurations, toFile.getAbsolutePath(),expected,null,e.getMessage(), "FAILED");
        }
        if(valid){
            log.info("{}",report);
        }else{
            log.error("{}",report);
        }

        gameEngineVerificationReports.add(report);
        return valid;
    }


    public void onApplicationEvent(GameEnginesLoadedEvent event) {
        if(!getRgsEngineProperties().isEnableVerification())
            return;

        gameEngineVerificationReports.forEach(gameEngineVerificationReport -> {

            if(gameEngineVerificationReport.getStatus().equals("FAILED")){
                if(gameEngineVerificationReport.getGameConfigurations()!=null) {
                    gameEngineVerificationReport.getGameConfigurations().forEach(gameConfiguration -> {
                        String gc = gameConfiguration.replace(".json", "");
                        gameEngineServices.remove(gc);
                    });
                }
            }
        });

        log.info("Saving reports {}",gameEngineVerificationReports);

        ServerReport report = new ServerReport();
        report.setRefId(event.getRefId());
        report.setTenant("default");
        report.setReports(gameEngineVerificationReports);
        report.setTriggered((String) event.getSource());
        report.setServerStatus(event.getStatus());
        report.setCreatedOn(new Date());

        mongoTemplate.insert(report,
                        "ServerReports");
    }
}
