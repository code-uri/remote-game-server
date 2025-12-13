package aimlabs.gaming.rgs.engine.artifact;

import aimlabs.gaming.rgs.core.entity.BaseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactMetaData extends BaseDto {

    String name;
    String version;
    String digest;
    Type type;
    boolean critical;

    public enum Type{
        ENGINE,
        RGS,
        RNG,
    }

    public ArtifactMetaData(String name, Type type) {
        this.name = name;
        this.type = type;
    }
}
