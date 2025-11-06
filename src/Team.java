import java.io.Serializable;

// Serializable을 구현해야 네트워크 전송이 가능합니다. (Enum은 기본 구현)
public enum Team implements Serializable {
    YELLOW(0xF2C14E),
    BLUE  (0x5DA3FA);

    public final int rgb;

    Team(int rgb) {
        this.rgb = rgb;
    }

    public Team opponent() {
        return this == YELLOW ? BLUE : YELLOW;
    }
}