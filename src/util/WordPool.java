package util;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import game.Board;

/**
 * 게임 단어 공급기 (WordPool)
 * 
 * [설계 목적]
 * - 게임 보드에 채워질 단어들을 관리하고 순차적으로 제공
 * - 서버와 클라이언트 간의 단어 배치 동기화를 위해 시드(Seed) 기반 셔플링 사용
 * 
 * [동기화 메커니즘]
 * - 초기 보드의 상태(토큰 문자열들)를 기반으로 해시(FNV-1a)를 계산하여 시드 생성
 * - 동일한 시드를 사용하므로, 서버와 클라이언트가 동일한 순서로 단어를 획득함
 */
public class WordPool {

    private final List<String> pool;
    private int idx = 0;

    private WordPool(List<String> pool) {
        this.pool = pool;
    }

    /**
     * 보드 상태로부터 시드를 계산하여 동기화된 WordPool 생성
     */
    public static WordPool fromBoard(Board board) {
        List<String> words = readWords();
        if (words.isEmpty()) {
            // 파일 로드 실패 시, 현재 보드의 토큰들로 풀 구성 (최소한의 동작 보장)
            words = new ArrayList<>();
            for (int r = 0; r < board.rows(); r++) {
                for (int c = 0; c < board.cols(); c++) {
                    words.add(board.get(r, c).token());
                }
            }
        }
        
        // 보드 상태 기반 시드 계산 (서버-클라이언트 동기화 핵심)
        long seed = computeSeed(board);
        Collections.shuffle(words, new Random(seed));
        return new WordPool(words);
    }

    /**
     * 다음 단어 반환
     * - 현재 칸의 단어(avoid)와 다른 단어를 찾을 때까지 탐색
     * - 풀을 모두 순회해도 없으면 그냥 다음 단어 반환
     */
    public synchronized String nextToken(String avoid) {
        if (pool.isEmpty()) return avoid;
        int attempts = pool.size();
        while (attempts-- > 0) {
            String candidate = pool.get(idx++ % pool.size());
            if (!candidate.equals(avoid)) return candidate;
        }
        return pool.get(idx++ % pool.size());
    }

    /**
     * word.txt 파일에서 단어 목록 로드
     * - 8글자 이하 단어만 필터링
     */
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

    /**
     * 보드 상태(모든 셀의 토큰)를 기반으로 64비트 해시값(시드) 계산
     * - FNV-1a 해시 알고리즘 사용
     */
    private static long computeSeed(Board board) {
        long h = 1469598103934665603L; // FNV-1a 64bit offset basis
        h ^= board.rows(); h *= 1099511628211L; // FNV prime
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
