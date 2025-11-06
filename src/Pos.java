import java.io.Serializable;

/**
 * 보드 상의 위치를 나타내는 불변 좌표.
 * - r: row(행), c: column(열) — 0부터 시작
 * - record를 쓰면 equals/hashCode/toString이 자동 생성되어 Set/Map에 쓰기 좋다.
 * - 네트워크 전송을 위해 Serializable 구현 (record는 기본 포함)
 */
public record Pos(int r, int c) implements Serializable { }