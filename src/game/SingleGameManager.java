package game;
import javax.swing.*;

import client.IGameClient;
import protocol.NetworkProtocol;
import ui.GameFrame;
import ui.LobbyFrame;
import util.WordPool;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * 싱글 플레이어 게임 관리자
 * 
 * [설계]
 * - 서버 없이 로컬에서 혼자 플레이할 수 있는 싱글 모드 제공
 * - IGameClient 인터페이스 구현으로 GameFrame과 동일한 방식으로 상호작용
 * - 난이도별 AI 상대 구현 (자동 공격 주기가 다름)
 * 
 * [AI 로직]
 * - 플레이어가 소유한 칸들 중 랜덤하게 하나를 골라 뒤집음
 * - 난이도에 따라 공격 주기가 달라짐 (쉬움 7초, 보통 4초, 어려움 2초)
 */
public class SingleGameManager implements IGameClient {

    /**
     * AI 난이도 (공격 주기로 구분)
     */
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
    private Timer gameTimer;    // 1초마다 시간 감소
    private Timer aiTimer;      // 난이도에 따라 주기적으로 AI 공격

    private final Team playerTeam = Team.YELLOW;
    private final Team aiTeam = Team.BLUE;

    public SingleGameManager(LobbyFrame lobbyFrame, String playerName, Difficulty difficulty) {
        this.lobbyFrame = lobbyFrame;
        this.playerName = playerName;
        this.difficulty = difficulty;
    }

    /**
     * 싱글 게임 시작
     * 로컬에서 보드 초기화 및 타이머 설정
     */
    public void start() {
        // 1. 보드 및 모델 초기화
        Board board = new Board(8, 12);
        TokenIndex index = new TokenIndex();
        fillBoardLocal(board, index);

        // 싱글 게임은 60초 고정
        model = new GameModel(board, index, 60, 1, WordPool.fromBoard(board));

        // 2. GameFrame 생성
        SwingUtilities.invokeLater(() -> {
            gameFrame = new GameFrame(model, this, playerTeam, playerName, "AI (" + difficulty.label + ")",
                    NetworkProtocol.Theme.PIRATE_LAIR);
            gameFrame.setVisible(true);
            lobbyFrame.setVisible(false);
        });

        // 3. 게임 타이머 시작 (1초마다 시간 감소)
        gameTimer = new Timer(1000, e -> {
            if (gameFrame != null)
                gameFrame.handleRemoteTick();

            if (model.secondsLeft() <= 0) {
                stopTimers();
                if (gameFrame != null)
                    gameFrame.handleRemoteGameOver();
            }
        });
        gameTimer.start();

        // 4. AI 타이머 시작 (난이도별 공격 주기)
        aiTimer = new Timer(difficulty.attackIntervalMs, e -> performAiAction());
        aiTimer.start();
    }

    /**
     * AI 자동 공격 로직
     * 
     * [전략]
     * - 플레이어(YELLOW)의 칸들 중 랜덤하게 하나를 선택하여 뒤집기
     * - 보드 전체를 순회하여 플레이어 소유 칸 목록을 수집
     * - 랜덤 선택으로 예측 불가능한 AI 동작 구현
     */
    private void performAiAction() {
        if (model.secondsLeft() <= 0)
            return;

        // 플레이어 소유의 모든 토큰 수집
        List<String> playerTokens = new java.util.ArrayList<>();
        Board board = model.board();
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                Cell cell = board.get(r, c);
                if (cell.owner() == playerTeam) {
                    playerTokens.add(cell.token());
                }
            }
        }

        // 랜덤하게 하나 선택하여 AI가 입력
        if (!playerTokens.isEmpty()) {
            String targetToken = playerTokens.get(new Random().nextInt(playerTokens.size()));
            SwingUtilities.invokeLater(() -> {
                if (gameFrame != null)
                    gameFrame.handleRemoteInput(aiTeam, targetToken);
            });
        }
    }

    // ===== IGameClient 인터페이스 구현 =====
    // GameFrame이 이 메소드들을 호출하여 로컬 게임 진행

    @Override
    public void sendInputRequest(Team team, String input) {
        // 로컬이므로 바로 처리 (서버 통신 없음)
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null)
                gameFrame.handleRemoteInput(team, input);
        });
    }

    @Override
    public void sendSentenceInput(Team team, String sentence) {
        // 싱글 모드에서는 보너스 타임이 구현되지 않았으므로 항상 성공 처리
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null)
                gameFrame.handleBonusSentenceResult(true, sentence, team);
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
        // 게임 중단 (X 버튼 클릭 시)
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
        if (gameTimer != null)
            gameTimer.stop();
        if (aiTimer != null)
            aiTimer.stop();
    }

    /**
     * 로컬 보드 초기화 (GameServer 로직 단순화 버전)
     * 
     * [처리 과정]
     * 1. resources/word.txt 파일에서 단어 로드 시도
     * 2. 실패 시 기본 단어 목록 사용
     * 3. 전체 셀 수만큼 단어를 확보 (부족하면 반복)
     * 4. 셔플 후 상/하반으로 나누어 YELLOW/BLUE 배정
     */
    private void fillBoardLocal(Board board, TokenIndex idx) {
        List<String> fallback = List.of("감자", "사과", "포도", "수박", "코코", "호랑이", "곰돌", "여우", "늑대", "토끼");

        Path wordPath = Path.of("resources", "word.txt");
        List<String> words = new ArrayList<>();

        try {
            if (Files.exists(wordPath)) {
                var lines = Files.readAllLines(wordPath, StandardCharsets.UTF_8);
                for (String w : lines) {
                    String trimmed = w.trim();
                    if (!trimmed.isEmpty() && trimmed.length() <= 4) {
                        words.add(trimmed);
                    }
                }
                System.out.println("싱글게임: " + wordPath + "에서 단어 " + words.size() + "개 불러옴.");
            } else {
                System.out.println("싱글게임: " + wordPath + " 파일을 찾지 못해 기본 단어를 사용합니다.");
            }
        } catch (Exception e) {
            System.err.println("싱글게임: " + wordPath + " 파일 읽기 실패: " + e.getMessage());
        }

        if (words.isEmpty()) {
            words = fallback;
        }

        // 단어 풀을 충분히 확보 (전체 셀 수만큼)
        int totalCells = board.rows() * board.cols();
        List<String> pool = new ArrayList<>(words);
        while (pool.size() < totalCells) {
            pool.addAll(words);
        }
        Collections.shuffle(pool);

        // 보드에 단어 배치 (상반은 YELLOW, 하반은 BLUE)
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
