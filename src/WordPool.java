import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 단어 공급기: word.txt(UTF-8) → 필터 → 섞은 뒤 순차 제공.
 * - seed를 보드 상태로부터 계산해 서버/클라이언트에서 동일 순서를 보장.
 * - 너무 긴 단어(>8자)는 제거하고, 없으면 현재 보드에 있는 토큰으로 대체.
 */
public class WordPool {

    private final List<String> pool;
    private int idx = 0;

    private WordPool(List<String> pool) {
        this.pool = pool;
    }

    public static WordPool fromBoard(Board board) {
        List<String> words = readWords();
        if (words.isEmpty()) {
            // 최소한 현재 보드 토큰으로라도 구성
            words = new ArrayList<>();
            for (int r = 0; r < board.rows(); r++) {
                for (int c = 0; c < board.cols(); c++) {
                    words.add(board.get(r, c).token());
                }
            }
        }
        long seed = computeSeed(board);
        Collections.shuffle(words, new Random(seed));
        return new WordPool(words);
    }

    /** 현재 토큰(avoid)와 다르거나, 더 이상 없으면 순환하여 반환 */
    public synchronized String nextToken(String avoid) {
        if (pool.isEmpty()) return avoid;
        int attempts = pool.size();
        while (attempts-- > 0) {
            String candidate = pool.get(idx++ % pool.size());
            if (!candidate.equals(avoid)) return candidate;
        }
        return pool.get(idx++ % pool.size());
    }

    private static List<String> readWords() {
        Path path = Path.of("resources", "word.txt");
        try {
            if (Files.exists(path)) {
                var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                List<String> ws = lines.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && s.length() <= 8)
                        .toList();
                if (!ws.isEmpty()) return new ArrayList<>(ws);
            }
        } catch (Exception ignored) { }
        return List.of();
    }

    private static long computeSeed(Board board) {
        long h = 1469598103934665603L; // FNV-1a 64bit offset
        h ^= board.rows(); h *= 1099511628211L;
        h ^= board.cols(); h *= 1099511628211L;
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                String t = board.get(r, c).token();
                for (int k = 0; k < t.length(); k++) {
                    h ^= t.charAt(k);
                    h *= 1099511628211L;
                }
            }
        }
        return h;
    }
}
