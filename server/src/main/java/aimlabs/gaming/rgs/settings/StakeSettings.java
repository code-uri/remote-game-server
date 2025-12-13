package aimlabs.gaming.rgs.settings;

import lombok.Data;

import java.io.Serializable;

@Data
public class StakeSettings implements Serializable {
    Double[] minMax;
    Double defaultStake;
    Double[] ladder;
    Integer[] minMaxLines;
    String[] licences;
    Double[] rtps;
}
