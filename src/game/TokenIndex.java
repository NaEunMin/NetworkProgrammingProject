package game;
import java.text.Normalizer;
import java.util.*;

/**
 * 토큰 검색 인덱스 (O(1) 조회를 위한 자료구조)
 *
 * [존재 이유]
 * "상대편 칸 중, 입력한 단어와 같은 토큰을 가진 칸"을 찾을 때
 * 보드 전체를 순회(O(rows*cols))하면 느리므로, HashMap 기반 인덱스로 O(1) 조회 구현
 *
 * [구조]
 * - byOwner: Team → (정규화된 토큰 → 좌표 목록)
 *   각 팀이 소유한 칸들을 토큰 기준으로 그룹화
 *
 * [정규화 전략]
 * - trim + NFKC + toLowerCase(Locale.ROOT) 조합 사용
 * - 이유: 한글/영문/전각/공백 혼용 가능성을 고려
 * - 효과: "Apple", "Ａｐｐｌｅ"(전각), " apple " 등이 동일하게 취급됨
 */
public class TokenIndex {

    private final Map<Team, Map<String, List<Pos>>> byOwner = new EnumMap<>(Team.class);

    public TokenIndex() {
        for (Team t : Team.values()) byOwner.put(t, new HashMap<>());
    }

    /**
     * 정규화 규칙 (입력과 보드 토큰 모두에 동일하게 적용)
     * 일관된 비교를 위해 static 메소드로 제공
     */
    public static String norm(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        String nfkc    = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    /**
     * 인덱스에 칸 등록 (보드 초기화 또는 뒤집기 후 호출)
     */
    public void add(Team owner, String rawToken, Pos pos) {
        String token = norm(rawToken);
        byOwner.get(owner).computeIfAbsent(token, k -> new ArrayList<>()).add(pos);
    }

    /**
     * 인덱스에서 칸 제거 (뒤집기 직전, 기존 소유 팀의 인덱스에서 제거)
     */
    public void remove(Team owner, String rawToken, Pos pos) {
        String token = norm(rawToken);
        var map  = byOwner.get(owner);
        var list = map.get(token);
        if (list == null) return;
        list.remove(pos);
        if (list.isEmpty()) map.remove(token);
    }

    /**
     * 특정 팀이 소유한 칸 중에서, 주어진 토큰을 가진 좌표들을 반환
     * 실전에서는 항상 "상대 팀"을 대상으로 조회함
     */
    public List<Pos> positionsOf(Team owner, String rawToken) {
        String token = norm(rawToken);
        var list = byOwner.get(owner).get(token);
        return (list == null) ? List.of() : List.copyOf(list);
    }
}
