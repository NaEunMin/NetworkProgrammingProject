
public enum Team {
    YELLOW(0xF2C14E),
    BLUE(0x5DA3FA),
    SPECIAL(0xFFFFFF);

    public final int rgb;

    Team(int rgb) {
        this.rgb = rgb;
    }

    public Team opponent() {
        if (this == SPECIAL)
            return null; // Special has no single opponent
        return this == YELLOW ? BLUE : YELLOW;
    }
}