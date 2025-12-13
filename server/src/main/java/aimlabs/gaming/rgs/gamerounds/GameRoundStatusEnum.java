package aimlabs.gaming.rgs.gamerounds;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GameRoundStatusEnum {
        INPROGRESS("INPROGRESS"),

        COMPLETED("COMPLETED"),

        CANCELLED("CANCELLED"),

        CLOSED("CLOSED"),

        ERROR("ERROR");

        private final String value;

        GameRoundStatusEnum(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }


        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static GameRoundStatusEnum fromValue(String value) {
            for (GameRoundStatusEnum b : GameRoundStatusEnum.values()) {
                if (b.value.equals(value)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }