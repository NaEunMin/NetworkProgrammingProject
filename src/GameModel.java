import java.util.List;

/**
 * "게임의 단 하나의 진실"을 보유하는 모델(서버/클라이언트 모두 공유).
 */
public class GameModel {

    private final Board board;
    private final TokenIndex index;
    private final WordPool wordPool;

    private int yellowCount;
    private int blueCount;
    private int yellowFlips;
    private int blueFlips;

    private int secondsLeft;
    private final int maxFlipPerInput;

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

    public Board board() { return board; }
    public synchronized int secondsLeft() { return secondsLeft; }
    public synchronized void tickOneSecond() { if (secondsLeft > 0) secondsLeft--; }
    public synchronized int getScore(Team team) {
        return (team == Team.YELLOW)? yellowCount : blueCount;
    }
    public synchronized int getFlips(Team team) {
        return (team == Team.YELLOW)? yellowFlips : blueFlips;
    }
    public synchronized void addScore(Team team, int score){
        if(team == Team.YELLOW){
            yellowCount += score;
        }
        else {
            blueCount += score;
        }
    }

    /**
     * 입력된 단어와 일치하는 상대 칸을 최대 maxFlipPerInput 만큼 뒤집고,
     * 뒤집힌 칸에는 새로운 단어를 채워 넣는다.
     */
    public synchronized java.util.List<FlipResult> flipByInput(Team myTeam, String rawInput) {
        java.util.List<FlipResult> results = new java.util.ArrayList<>();
        if (rawInput == null || rawInput.isBlank()) return results;

        Team opponent = myTeam.opponent();
        List<Pos> targets = index.positionsOf(opponent, rawInput);
        if (targets.isEmpty()) return results;

        int flipped = 0;
        for (Pos p : targets) {
            if (flipped >= maxFlipPerInput) break;

            Cell cell = board.get(p.r(), p.c());
            Team prevOwner = cell.owner();
            String oldToken = cell.token();

            // 다음 단어를 뽑아서 교체
            String newToken = wordPool.nextToken(oldToken);

            // 인덱스 업데이트
            index.remove(opponent, oldToken, p);
            index.add(myTeam, newToken, p);

            // 상태 반영
            cell.setOwner(myTeam);
            cell.setToken(newToken);
            results.add(new FlipResult(p, prevOwner, myTeam, oldToken, newToken));

            if(myTeam == Team.YELLOW) {
                yellowCount += 100;
                yellowFlips++;
            }
            else {
                blueCount += 100;
                blueFlips++;
            }
            flipped++;
        }

        return results;
    }

    public static record FlipResult(Pos pos, Team from, Team to, String fromToken, String toToken) {}
}
