package game;
import java.io.Serializable;

/**
 * 보드 상의 위치를 나타내는 불변 좌표 (r=행, c=열, 0부터 시작)
 * 
 * [record 사용 이유]
 * - 좌표는 생성 후 변경되지 않아야 하므로 불변성이 중요함
 * - equals/hashCode가 자동 생성되어 Set/Map의 키로 안전하게 사용 가능
 * - 네트워크를 통해 직렬화되어 전송되므로 Serializable 구현 필요
 */
public record Pos(int r, int c) implements Serializable { }