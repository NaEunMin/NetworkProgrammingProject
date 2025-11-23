import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 싱글 플레이어 게임 관리자.
 * - 서버 없이 로컬에서 GameModel과 GameFrame을 생성하고 관리한다.
 * - AI 로직(난이도별 자동 공격)을 수행한다.
 */
public class SingleGameManager implements IGameClient {

    public enum Difficulty {
        EASY(7000, "쉬움"),
        NORMAL(4000, "보통"),
        HARD(2000, "어려움");

        final int attackIntervalMs;
        final String label;

        Difficulty(int ms, String label) {
            this.attackIntervalMs = ms;
            this.label = label;
        }
    }

    private final LobbyFrame lobbyFrame;
    private final Difficulty difficulty;
    private final String playerName;

    private GameModel model;
    private GameFrame gameFrame;
    private Timer gameTimer;
    private Timer aiTimer;

    private final Team playerTeam = Team.YELLOW;
    private final Team aiTeam = Team.BLUE;

    public SingleGameManager(LobbyFrame lobbyFrame, String playerName, Difficulty difficulty) {
        this.lobbyFrame = lobbyFrame;
        this.playerName = playerName;
        this.difficulty = difficulty;
    }

    public void start() {
        // 1. 보드 및 모델 초기화
        Board board = new Board(8, 12);
        TokenIndex index = new TokenIndex();
        // 로컬에서 단어 채우기 (GameServer 로직 일부 재사용 또는 복제)
        fillBoardLocal(board, index);

        // 싱글 게임은 60초
        model = new GameModel(board, index, 60, 1, WordPool.fromBoard(board));

        // 2. GameFrame 생성
        SwingUtilities.invokeLater(() -> {
            gameFrame = new GameFrame(model, this, playerTeam, playerName, "AI (" + difficulty.label + ")");
            gameFrame.setVisible(true);
            lobbyFrame.setVisible(false);
        });

        // 3. 게임 타이머 시작 (1초마다)
        gameTimer = new Timer(1000, e -> {
            // model.tickOneSecond(); // GameFrame.handleRemoteTick() 내부에서 수행하므로 중복 제거
            if (gameFrame != null) gameFrame.handleRemoteTick();

            if (model.secondsLeft() <= 0) {
                stopTimers();
                if (gameFrame != null) gameFrame.handleRemoteGameOver();
            }
        });
        gameTimer.start();

        // 4. AI 타이머 시작
        aiTimer = new Timer(difficulty.attackIntervalMs, e -> performAiAction());
        aiTimer.start();
    }

    private void performAiAction() {
        if (model.secondsLeft() <= 0) return;

        // AI는 플레이어(YELLOW)의 땅 중 하나를 골라 뒤집는다.
        // 1. 현재 보드에서 플레이어 소유의 셀들을 찾는다.
        // 2. 그 중 하나를 랜덤 선택하여 그 단어를 입력한다.
        
        // 간단 구현: 전체 보드 순회
        List<String> playerTokens = new java.util.ArrayList<>();
        Board board = model.board();
        for(int r=0; r<board.rows(); r++){
            for(int c=0; c<board.cols(); c++){
                Cell cell = board.get(r, c);
                if(cell.owner() == playerTeam){
                    playerTokens.add(cell.token());
                }
            }
        }

        if (!playerTokens.isEmpty()) {
            String targetToken = playerTokens.get(new Random().nextInt(playerTokens.size()));
            // AI가 입력한 것처럼 처리
            SwingUtilities.invokeLater(() -> {
                if (gameFrame != null) gameFrame.handleRemoteInput(aiTeam, targetToken);
            });
        }
    }

    // IGameClient 구현

    @Override
    public void sendInputRequest(Team team, String input) {
        // 로컬 처리이므로 바로 반영
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) gameFrame.handleRemoteInput(team, input);
        });
    }

    @Override
    public void sendSentenceInput(Team team, String sentence) {
        // 싱글 모드에서는 보너스 타임이 없거나, 있어도 단순 성공 처리
        // 여기서는 일단 성공으로 처리
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) gameFrame.handleBonusSentenceResult(true, sentence, team);
        });
    }

    @Override
    public void gameHasFinished() {
        // 게임 종료 후 로비 복귀
        stopTimers();
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) {
                gameFrame.dispose();
                gameFrame = null;
            }
            lobbyFrame.setVisible(true);
        });
    }

    @Override
    public void disconnectFromGame() {
        // 게임 중단
        stopTimers();
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) {
                gameFrame.dispose();
                gameFrame = null;
            }
            lobbyFrame.setVisible(true);
        });
    }

    private void stopTimers() {
        if (gameTimer != null) gameTimer.stop();
        if (aiTimer != null) aiTimer.stop();
    }

    // GameServer의 fillBoardFromFilesOrFallback 로직을 간소화하여 가져옴
    private void fillBoardLocal(Board board, TokenIndex idx) {
        // 간단하게 기본 단어로 채움 (파일 읽기 생략 가능하거나, 필요시 구현)
        List<String> fallback = List.of("감자", "사과", "포도", "수박", "코코", "호랑이", "곰돌", "여우", "늑대", "토끼");
        
        int totalCells = board.rows() * board.cols();
        List<String> pool = new java.util.ArrayList<>(fallback);
        while (pool.size() < totalCells) {
            pool.addAll(fallback);
        }
        Collections.shuffle(pool);

        int tokenIdx = 0;
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                String token = pool.get(tokenIdx++ % pool.size());
                Team owner = (r < board.rows() / 2) ? Team.YELLOW : Team.BLUE;
                Cell cell = new Cell(owner, token);
                board.set(r, c, cell);
                idx.add(owner, token, new Pos(r, c));
            }
        }
    }
}
