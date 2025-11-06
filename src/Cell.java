import java.io.Serializable;

/**
 * 보드의 "한 칸"을 이루는 최소 단위.
 * (중략)
 * - 네트워크 전송을 위해 Serializable 구현
 */
public class Cell implements Serializable {
    // 직렬화 ID
    private static final long serialVersionUID = 1L;
    
    private Team owner;
    private final String token;

    public Cell(Team owner, String token) {
        this.owner = owner;
        this.token = token;
    }

    public Team owner()      { return owner; }
    public String token()    { return token; }

    /** 뒤집기(소유권 변경) — 실제 보드 현실 상태를 갱신한다. */
    public void setOwner(Team owner) { this.owner = owner; }
}