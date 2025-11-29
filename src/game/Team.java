package game;

/**
 * 게임 팀을 나타내는 열거형
 * 
 * [SPECIAL 팀의 역할]
 * - SPECIAL은 실제 플레이어 팀이 아니라, 보너스 타임에 등장하는 특수 칸을 표시하기 위한 마커
 * - 보너스 타임 시작 시 랜덤한 위치에 SPECIAL 칸이 생성되며, 이를 먼저 뒤집는 팀에게 보너스 점수 부여
 * 
 * [rgb 필드]
 * - 각 팀의 색상을 미리 정의하여 UI에서 일관된 색상 사용 보장
 */
public enum Team {
    YELLOW(0xF2C14E),
    BLUE(0x5DA3FA),
    SPECIAL(0xFFFFFF);

    public final int rgb;

    Team(int rgb) {
        this.rgb = rgb;
    }

    /**
     * 상대 팀을 반환 (YELLOW ↔ BLUE)
     * SPECIAL은 대전 상대가 없으므로 null 반환
     */
    public Team opponent() {
        if (this == SPECIAL)
            return null; // Special has no single opponent
        return this == YELLOW ? BLUE : YELLOW;
    }
}