import java.util.List;

/**
 * "게임의 단일 진실"을 보유하는 모델(스레드-세이프).
 * - 사진의 룰을 반영: 1:1, 턴 없음(실시간), 입력 즉시 뒤집기.
 * - 보드/인덱스/점수/라운드 타이머에 대한 상태를 여기에서 관리하고,
 *   GUI는 이 모델의 공개 API만 호출해 상태를 바꾼다(MVC).
 *
 * 동시성
 * - Swing은 EDT(이벤트 디스패치 스레드)에서 이벤트가 들어오므로
 *   대부분 한 스레드이지만, 추후 네트워크/타이머/AI가 추가될 수 있어
 *   핵심 메소드에 synchronized를 걸어 원자성/일관성을 보장했다.
 */
public class GameModel {

    /** 실 보드(현실 상태) + 빠른 조회를 위한 토큰 인덱스 */
    private final Board board;
    private final TokenIndex index;

    /** 점수는 "팀이 소유한 칸 수"로 정의 — 사진 상단의 점수판을 반영 */
    private int yellowCount;
    private int blueCount;

    /** 라운드 잔여 초(예: 60초). GUI 타이머가 1초마다 감소시키고 0초에서 라운드 종료. */
    private int secondsLeft;

    /** 한 번 입력으로 뒤집는 최대 칸 수(사진은 1칸씩 뒤집는 느낌이 강해 기본 1로 둠) */
    private final int maxFlipPerInput;

    public GameModel(Board board, TokenIndex index, int seconds, int maxFlipPerInput) {
        this.board = board;
        this.index = index;
        this.secondsLeft = seconds;
        this.maxFlipPerInput = Math.max(1, maxFlipPerInput);
        recomputeCounts(); // 초기 점수 계산
    }

    /** 현 보드 반환(렌더링용) */
    public Board board() { return board; }

    /** 남은 시간(초) — 점수판 중앙 타이머에 표시 */
    public synchronized int secondsLeft() { return secondsLeft; }

    /** 타이머 1초 감소(0 밑으로는 내려가지 않음) */
    public synchronized void tickOneSecond() {
        if (secondsLeft > 0) secondsLeft--;
    }

    /** 팀별 소유 칸 수(사진의 "0P / 500P"를 칸 수로 해석) */
    public synchronized int count(Team team) {
        return (team == Team.YELLOW) ? yellowCount : blueCount;
    }

    /**
     * "입력 성공 → 상대 칸 중 해당 토큰을 가진 칸을 즉시 내 소유로 뒤집기"
     * - 무턴/실시간을 반영: 어떤 팀이든 언제든 호출 가능(두 입력창이 동시에 눌려도 안전).
     * - 원자적 동작: 보드/인덱스/점수 3요소를 한 덩어리로 갱신한다.
     *
     * @param myTeam 입력한 쪽(내 팀)
     * @param rawInput 사용자가 친 문자열
     * @return 실제 뒤집힌 칸 수(0이면 상대판에 같은 단어가 없었다는 뜻)
     */
    public synchronized int flipByInput(Team myTeam, String rawInput) {
        if (rawInput == null || rawInput.isBlank()) return 0;

        // 1) 상대 팀 보드에서 같은 토큰을 가진 좌표를 조회
        Team opponent = myTeam.opponent();
        List<Pos> targets = index.positionsOf(opponent, rawInput);
        if (targets.isEmpty()) return 0;

        // 2) 비즈니스 룰:
        //    - 사진의 "입력하기" 버튼 터치감에 가깝게 1회 입력→1칸만 뒤집는 설정(기본).
        //    - 난이도 옵션으로 N칸까지 확장 가능(maxFlipPerInput).
        int flipped = 0;
        for (Pos p : targets) {
            if (flipped >= maxFlipPerInput) break;

            // 보드 현실 상태(셀)를 가져와 소유권 변경
            Cell cell = board.get(p.r(), p.c());

            // 인덱스 일관성: 기존 소유자 목록에서 제거 → 내 소유자 목록에 추가
            index.remove(opponent, cell.token(), p);
            index.add(myTeam, cell.token(), p);

            // 셀 실제 소유권 변경
            cell.setOwner(myTeam);

            flipped++;
        }

        // 3) 점수 재계산(보드 전체 스캔 대신 증감식으로 최적화 가능하지만,
        //    교육용으로 가독성 우선: 한 번 뒤집을 때 칸 수가 작으므로 충분히 빠르다.)
        recomputeCounts();

        return flipped;
    }

    /** 전체 보드를 훑어 팀별 칸 수를 다시 계산(점수판 갱신용) */
    private void recomputeCounts() {
        int y = 0, b = 0;
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                Team t = board.get(r, c).owner();
                if (t == Team.YELLOW) y++; else b++;
            }
        }
        yellowCount = y;
        blueCount   = b;
    }
}
