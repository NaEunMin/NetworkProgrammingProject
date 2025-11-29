package game;
import java.io.Serializable;

/**
 * 보드의 "한 칸"을 나타내는 최소 단위
 * 
 * [가변 클래스로 설계]
 * - 게임 중 소유권(owner)이 계속 변경될 수 있으므로 불변 객체로 만들 수 없음
 * - record 대신 일반 class를 사용하여 setOwner() 메소드 제공
 * - 네트워크 전송을 위해 Serializable 구현
 */
public class Cell implements Serializable {
    // 직렬화 ID
    private static final long serialVersionUID = 1L;
    
    private Team owner;
    private String token;

    public Cell(Team owner, String token) {
        this.owner = owner;
        this.token = token;
    }

    public Team owner()      { return owner; }
    public String token()    { return token; }

    /** 뒤집기(소유권 변경) — 실제 보드 현실 상태를 갱신한다. */
    public void setOwner(Team owner) { this.owner = owner; }
    public void setToken(String token) { this.token = token; }
}
