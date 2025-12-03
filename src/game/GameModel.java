package game;

import java.util.List;

import util.WordPool;

/**
 * 게임의 핵심 상태 모델 (서버/클라이언트 공유)
 * 
 * [설계]
 * - "단일 진실 공급원(Single Source of Truth)" 패턴
 * - 서버는 이 모델을 수정하고, 클라이언트는 서버로부터 받은 변경사항을 동기화
 * - synchronized 메소드로 멀티스레드 환경에서 안전성 보장 (서버 측 동시 입력 처리)
 * 
 * [핵심 구성요소]
 * - Board: 격자 상태 (소유권)
 * - TokenIndex: 빠른 토큰 검색을 위한 인덱스 (O(1) 조회)
 * - WordPool: 뒤집힌 칸에 새 단어를 채우기 위한 단어 풀
 */
public class GameModel {

    private final Board board;
    private final TokenIndex index;
    private final WordPool wordPool;

    // 점수 (100점 단위로 증가)
    private int yellowCount;
    private int blueCount;

    // 뒤집은 칸 수 (통계용)
    private int yellowFlips;
    private int blueFlips;

    private int secondsLeft;
    private final int maxFlipPerInput; // 한 번 입력으로 뒤집을 수 있는 최대 칸 수

    public GameModel(Board board, TokenIndex index, int seconds, int maxFlipPerInput, WordPool wordPool) {
        this.board = board;
        this.index = index;
        this.secondsLeft = seconds;
        this.maxFlipPerInput = Math.max(1, maxFlipPerInput);
        this.wordPool = wordPool;
        this.yellowCount = 0;
        this.blueCount = 0;
        this.yellowFlips = 0;
        this.blueFlips = 0;
    }

    public Board board() {
        return board;
    }

    public synchronized int secondsLeft() {
        return secondsLeft;
    }

    public synchronized void tickOneSecond() {
        if (secondsLeft > 0)
            secondsLeft--;
    }

    public synchronized int getScore(Team team) {
        return (team == Team.YELLOW) ? yellowCount : blueCount;
    }

    public synchronized int getFlips(Team team) {
        return (team == Team.YELLOW) ? yellowFlips : blueFlips;
    }

    public synchronized void addScore(Team team, int score) {
        if (team == Team.YELLOW) {
            yellowCount += score;
        } else {
            blueCount += score;
        }
    }

    /**
     * 게임의 핵심 로직: 단어 입력으로 상대방 칸 뒤집기
     * 
     * [처리 과정]
     * 1. TokenIndex로 입력 단어와 일치하는 상대 칸 검색 (O(1))
     * 2. SPECIAL 칸도 검색 (누구나 뒤집을 수 있음)
     * 3. 최대 maxFlipPerInput개까지 뒤집기
     * 4. 뒤집힌 칸에 새 단어 배치 (WordPool에서 추출)
     * 5. TokenIndex 업데이트 (이전 소유자에서 제거, 새 소유자에 추가)
     * 6. 점수 부여 (일반 칸 100점, SPECIAL 칸 300점)
     * 
     * @return 뒤집힌 칸들의 정보 리스트 (클라이언트가 애니메이션 재생에 사용)
     */
    public synchronized java.util.List<FlipResult> flipByInput(Team myTeam, String rawInput) {
        java.util.List<FlipResult> results = new java.util.ArrayList<>();
        if (rawInput == null || rawInput.isBlank())
            return results;

        Team opponent = myTeam.opponent();

        // 1. 상대방(Opponent) 토큰 검색
        List<Pos> targets = new java.util.ArrayList<>(index.positionsOf(opponent, rawInput));

        // 2. 스페셜(SPECIAL) 토큰 검색 (누구나 뒤집을 수 있음)
        List<Pos> specialTargets = index.positionsOf(Team.SPECIAL, rawInput);
        targets.addAll(specialTargets);

        if (targets.isEmpty())
            return results;

        // 발견된 순서대로 뒤집기 (랜덤하게 하려면 Collections.shuffle(targets) 사용)
        int flipped = 0;
        for (Pos p : targets) {
            if (flipped >= maxFlipPerInput)
                break;

            Cell cell = board.get(p.r(), p.c());
            Team prevOwner = cell.owner();
            String oldToken = cell.token();

            // WordPool에서 다음 단어를 뽑아서 교체
            String newToken = wordPool.nextToken(oldToken);

            // Cell 상태 반영
            // 5% 확률로 보너스 격자판(SPECIAL) 생성
            Team newOwner = myTeam;
            if (Math.random() < 0.05) {
                newOwner = Team.SPECIAL;
            }

            cell.setOwner(newOwner);
            cell.setToken(newToken);

            // 인덱스 업데이트 (이전 소유자 인덱스에서 제거, 새 소유자 인덱스에 추가)
            index.remove(prevOwner, oldToken, p);
            index.add(newOwner, newToken, p);

            results.add(new FlipResult(p, prevOwner, newOwner, oldToken, newToken));

            // 점수 부여 (SPECIAL 칸은 3배)
            int score = 100;
            if (prevOwner == Team.SPECIAL) {
                score = 300;
            }

            if (myTeam == Team.YELLOW) {
                yellowCount += score;
                yellowFlips++;
            } else {
                blueCount += score;
                blueFlips++;
            }
            flipped++;
        }

        return results;
    }

    /**
     * 뒤집기 결과 정보 (클라이언트가 애니메이션 재생에 사용)
     */
    public static record FlipResult(Pos pos, Team from, Team to, String fromToken, String toToken) {
    }

    /**
     * 보너스 타임용: 특정 칸을 SPECIAL 칸으로 변환
     * (서버 전용 메소드, 보너스 타임 시작 시 랜덤 위치에 SPECIAL 칸 생성)
     */
    public synchronized void spawnSpecial(Pos pos) {
        Cell cell = board.get(pos.r(), pos.c());
        Team oldOwner = cell.owner();
        String token = cell.token();

        // 인덱스 갱신
        index.remove(oldOwner, token, pos);
        index.add(Team.SPECIAL, token, pos);

        // 셀 갱신
        cell.setOwner(Team.SPECIAL);
    }
}
