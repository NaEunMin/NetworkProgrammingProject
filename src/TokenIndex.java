import java.text.Normalizer;
import java.util.*;

/**
 * "상대편 칸 중, 입력한 단어와 같은 텍스트를 가진 칸"을
 * 보드를 전수검색(O(rows*cols))하지 않고 빠르게 찾기 위한 인덱스.
 *
 * 구조
 * - byOwner: 팀 → (정규화된 토큰 문자열 → 좌표 목록)
 *   => 팀별로 자기 소유 칸들을 토큰 기준으로 묶어 둔다.
 *
 * 정규화(norm) 전략
 * - 사진 속 UI처럼 한글/영문/전각/공백 혼용 가능성을 고려해
 *   trim + NFKC + toLowerCase(Locale.ROOT) 조합을 사용.
 * - 이렇게 하면 "Apple", "Ａｐｐｌｅ"(전각), " apple " 등이 동일하게 취급된다.
 */
public class TokenIndex {

    private final Map<Team, Map<String, List<Pos>>> byOwner = new EnumMap<>(Team.class);

    public TokenIndex() {
        for (Team t : Team.values()) byOwner.put(t, new HashMap<>());
    }

    /** 입력·보드 토큰 모두에 동일 적용될 정규화 규칙 */
    public static String norm(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        String nfkc    = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    /** 보드 초기화/뒤집기 후 인덱스에 칸을 등록 */
    public void add(Team owner, String rawToken, Pos pos) {
        String token = norm(rawToken);
        byOwner.get(owner).computeIfAbsent(token, k -> new ArrayList<>()).add(pos);
    }

    /** 뒤집기 직전에 ‘기존 소유 팀’의 인덱스에서 제거 */
    public void remove(Team owner, String rawToken, Pos pos) {
        String token = norm(rawToken);
        var map  = byOwner.get(owner);
        var list = map.get(token);
        if (list == null) return;
        list.remove(pos);
        if (list.isEmpty()) map.remove(token);
    }

    /**
     * 특정 팀이 소유한 칸 중에서, 주어진 토큰을 가진 좌표들을 반환.
     * - 실전에서는 항상 "상대 팀"을 대상으로 조회한다.
     */
    public List<Pos> positionsOf(Team owner, String rawToken) {
        String token = norm(rawToken);
        var list = byOwner.get(owner).get(token);
        return (list == null) ? List.of() : List.copyOf(list);
    }
}
