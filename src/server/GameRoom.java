package server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import game.Board;
import game.GameModel;
import game.Cell;
import game.Pos;
import game.Team;
import protocol.*;
import util.SentencePool;

/**
 * 게임방 관리 클래스 (서버 측)
 * 
 * [설계]
 * - 하나의 GameRoom은 최대 2명의 플레이어 (YELLOW vs BLUE) 관리
 * - 방 생성 → 대기 → 게임 시작 → 게임 진행 → 게임 종료 → 방 삭제 라이프사이클
 * - Timer를 사용하여 1초마다 시간 감소 및 클라이언트 동기화
 * - 보너스 타임 지원 (10초 전 시작, 10초간 진행)
 * 
 * [핵심 기능]
 * 1. 플레이어 관리: 입장/퇴장, 준비 상태
 * 2. 게임 타이머: 1초마다 Tick 브로드캐스트
 * 3. 입력 처리: GameModel.flipByInput() 호출 및 결과 브로드캐스트
 * 4. 보너스 타임: 문장 매칭 및 SPECIAL 칸 생성
 */
public class GameRoom {

    private final String roomName;
    private final String password;
    private GameModel gameModel; // non-final (게임 재시작 시 리셋 가능)
    private final int durationSec;
    private final boolean bonusEnabled;
    private final NetworkProtocol.Theme theme;
    private final GameServer server;
    private final SentencePool sentencePool;
    private final int maxPlayers = 2;

    // 플레이어 관리
    private ClientHandler playerYellow;
    private ClientHandler playerBlue;
    private ClientHandler owner; // 방장 (게임 시작 권한)
    private final java.util.Map<ClientHandler, Boolean> readyStates = new java.util.HashMap<>();

    // 게임 상태
    private boolean isPlaying = false;
    private Timer gameTimer;
    private int initialGameTime;

    // 보너스 타임 관련
    private boolean isBonusTime = false;
    private boolean bonusTimeActivated = false; // 보너스 타임이 한 번이라도 시작되었는지
    private Timer bonusTimer;
    private List<String> bonusSentences = Collections.synchronizedList(new ArrayList<>());

    public GameRoom(String roomName, String password, GameModel gameModel, int durationSec, boolean bonusEnabled,
            NetworkProtocol.Theme theme, GameServer server,
            SentencePool sentencePool) {
        this.roomName = roomName;
        this.password = password;
        this.gameModel = gameModel;
        this.durationSec = durationSec;
        this.bonusEnabled = bonusEnabled;
        this.theme = theme;
        this.server = server;
        this.sentencePool = sentencePool;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * 플레이어를 방에 추가
     * 첫 번째 플레이어가 자동으로 방장이 됨
     */
    public synchronized boolean addPlayer(ClientHandler player, Team team) {
        if (team == Team.YELLOW && playerYellow == null) {
            playerYellow = player;
            player.setCurrentRoom(this);
            if (owner == null)
                owner = player;
            readyStates.put(player, false);
            return true;
        } else if (team == Team.BLUE && playerBlue == null) {
            playerBlue = player;
            player.setCurrentRoom(this);
            if (owner == null)
                owner = player;
            readyStates.put(player, false);
            return true;
        }
        return false;
    }

    /**
     * 플레이어가 방에서 나갈 때 처리
     * 
     * [처리 과정]
     * 1. 플레이어 제거
     * 2. 게임 중이면 상대방에게 알림 후 게임 중단
     * 3. 대기 중이면 방장 이전 처리
     * 4. 방이 비면 서버에서 방 제거
     */
    public synchronized void removePlayer(ClientHandler player) {
        ClientHandler opponent = null;
        if (player == playerYellow) {
            playerYellow = null;
            opponent = playerBlue;
        } else if (player == playerBlue) {
            playerBlue = null;
            opponent = playerYellow;
        }

        player.setCurrentRoom(null);
        readyStates.remove(player);

        if (isPlaying) {
            // 게임 중 한 명이 나가면 게임 중단
            stopGame();
            if (opponent != null) {
                opponent.sendMessage(new NetworkProtocol.Msg_S2C_OpponentLeft());
            }
        } else {
            // 대기 중: 방장 이전
            if (player == owner) {
                owner = (playerYellow != null) ? playerYellow : playerBlue;
            }
            broadcastPlayerList();
        }

        // 방이 비면 서버에서 제거
        if (playerYellow == null && playerBlue == null) {
            server.removeRoom(this);
        } else {
            server.broadcastRoomUpdated(this);
        }
    }

    public synchronized boolean isFull() {
        return playerYellow != null && playerBlue != null;
    }

    /**
     * 비어있는 팀 반환 (상대 팀 찾기)
     */
    public synchronized Team getOppositeTeam(Team team) {
        if (team == Team.YELLOW && playerBlue == null)
            return Team.BLUE;
        if (team == Team.BLUE && playerYellow == null)
            return Team.YELLOW;
        return null;
    }

    public synchronized int getPlayerCount() {
        int count = 0;
        if (playerYellow != null)
            count++;
        if (playerBlue != null)
            count++;
        return count;
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 로비 UI용 방 정보 생성
     */
    public synchronized NetworkProtocol.RoomInfo toRoomInfo() {
        int left = isPlaying ? gameModel.secondsLeft() : durationSec;
        return new NetworkProtocol.RoomInfo(roomName, left, durationSec, bonusEnabled, theme,
                getPlayerCount(), maxPlayers, isPlaying);
    }

    /**
     * 대기실 UI용 플레이어 목록 생성
     */
    public synchronized List<NetworkProtocol.PlayerInfo> snapshotPlayers() {
        List<NetworkProtocol.PlayerInfo> list = new ArrayList<>();
        if (playerYellow != null)
            list.add(new NetworkProtocol.PlayerInfo(playerYellow.getNickname(), Team.YELLOW,
                    readyStates.get(playerYellow), playerYellow == owner));
        if (playerBlue != null)
            list.add(new NetworkProtocol.PlayerInfo(playerBlue.getNickname(), Team.BLUE,
                    readyStates.get(playerBlue), playerBlue == owner));
        return list;
    }

    /**
     * 게임 시작 (방장만 가능, 2명 모두 준비 완료 필요)
     * 
     * [처리 과정]
     * 1. 방장 권한 확인
     * 2. 2명 모두 참여 확인
     * 3. 게임 시작 메시지 브로드캐스트
     * 4. 1초마다 Tick 타이머 시작
     * 5. 로비에 방 상태 갱신
     */
    public synchronized void startGameBy(ClientHandler requester) {
        if (requester != owner) {
            requester.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("방장만 게임을 시작할 수 있습니다."));
            return;
        }

        if (!isFull()) {
            requester.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("2명이 모두 참여해 주세요."));
            return;
        }

        isPlaying = true;
        initialGameTime = durationSec;
        bonusTimeActivated = false;

        // 보드 리셋 (새 게임 모델 생성)
        gameModel = server.createGameModel(durationSec);

        // 게임 시작 메시지 전송
        if (playerYellow != null) {
            playerYellow.sendMessage(
                    new NetworkProtocol.Msg_S2C_GameStart(Team.YELLOW, gameModel.board(), gameModel.secondsLeft(),
                            theme));
        }
        if (playerBlue != null) {
            playerBlue.sendMessage(
                    new NetworkProtocol.Msg_S2C_GameStart(Team.BLUE, gameModel.board(), gameModel.secondsLeft(),
                            theme));
        }

        System.out.println("서버: 방[" + roomName + "] 게임 시작!");

        // 1초마다 Tick 타이머
        gameTimer = new Timer(true);
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameRoom.this) {
                    gameModel.tickOneSecond();
                    broadcast(new NetworkProtocol.Msg_S2C_Tick());

                    int left = gameModel.secondsLeft();

                    // 보너스 타임 시작 (게임 시간의 절반이 지났을 때)
                    if (bonusEnabled && !bonusTimeActivated && left == (durationSec / 2)) {
                        startBonusTime();
                    }

                    // 10% 확률로 추가 점수 격자판(SPECIAL 칸) 생성
                    if (bonusEnabled && Math.random() < 0.1) {
                        spawnSpecialItem();
                    }

                    // 게임 종료
                    if (left <= 0) {
                        stopGame();
                        broadcast(new NetworkProtocol.Msg_S2C_GameOver());
                    }
                }
            }
        }, 1000, 1000);

        server.broadcastRoomUpdated(this);
    }

    /**
     * 게임 중단 (시간 종료 또는 플레이어 이탈)
     */
    public synchronized void stopGame() {
        isPlaying = false;

        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }

        if (bonusTimer != null) {
            bonusTimer.cancel();
            bonusTimer = null;
        }

        isBonusTime = false;
        bonusTimeActivated = false;
        bonusSentences.clear();

        System.out.println("서버: 방[" + roomName + "] 게임 종료.");
        server.broadcastRoomUpdated(this);
    }

    /**
     * 방의 모든 플레이어에게 메시지 브로드캐스트
     */
    public synchronized void broadcast(Serializable message) {
        if (playerYellow != null) {
            playerYellow.sendMessage(message);
        }
        if (playerBlue != null) {
            playerBlue.sendMessage(message);
        }
    }

    /**
     * 대기실 플레이어 목록 갱신 브로드캐스트
     */
    public synchronized void broadcastPlayerList() {
        var msg = new NetworkProtocol.Msg_S2C_PlayerListUpdated(snapshotPlayers());
        broadcast(msg);
    }

    /**
     * 준비 상태 토글 (대기실)
     */
    public synchronized void setReady(ClientHandler player, boolean ready) {
        readyStates.put(player, ready);
        broadcastPlayerList();
    }

    /**
     * 게임 중 단어 입력 처리
     * GameModel.flipByInput() 호출 후 결과를 BroadcastInput으로 전달
     */
    public synchronized void handleInput(ClientHandler player, Team team, String input) {
        if (!isPlaying)
            return;

        // 입력을 모두에게 전달 (각 클라이언트가 GameModel.flipByInput() 호출)
        broadcast(new NetworkProtocol.Msg_S2C_BroadcastInput(team, input));
    }

    /**
     * 대기실 채팅 브로드캐스트
     */
    public synchronized void broadcastWaitingChat(String sender, String text) {
        broadcast(new NetworkProtocol.Msg_S2C_WaitingChat(sender, text));
    }

    /**
     * 보너스 타임 시작 (10초간 진행)
     * 
     * [처리 과정]
     * 1. SentencePool에서 5개 문장 선택
     * 2. SPECIAL 칸 3개 생성
     * 3. 문장 목록 브로드캐스트
     * 4. 10초 후 자동 종료
     */
    public synchronized void startBonusTime() {
        isBonusTime = true;
        bonusTimeActivated = true;

        bonusSentences = new ArrayList<>(sentencePool.getRandomSentences(5));
        System.out.println("서버: 방[" + roomName + "] 보너스 타임 시작! 문장: " + bonusSentences);

        // SPECIAL 칸 생성
        spawnSpecialItem();

        broadcast(new NetworkProtocol.Msg_S2C_BonusTimeStart(bonusSentences));

        // 20초 후 보너스 타임 종료
        bonusTimer = new Timer(true);
        bonusTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                endBonusTime();
            }
        }, 20000);
    }

    /**
     * 보너스 타임 종료
     */
    public synchronized void endBonusTime() {
        isBonusTime = false;
        if (bonusTimer != null) {
            bonusTimer.cancel();
            bonusTimer = null;
        }
        bonusSentences.clear();
        System.out.println("서버: 방[" + roomName + "] 보너스 타임 종료.");
        broadcast(new NetworkProtocol.Msg_S2C_BonusTimeEnd());
    }

    /**
     * 보너스 타임 문장 입력 처리
     * 
     * [처리 과정]
     * 1. 입력 문장 정규화 (trim + normalize)
     * 2. bonusSentences에서 매칭 확인
     * 3. 성공 시 점수 부여 (500점) 및 문장 제거
     * 4. 결과 브로드캐스트
     */
    public synchronized void handleSentenceInput(ClientHandler player, Team team, String sentence) {
        if (!isBonusTime || !isPlaying)
            return;

        System.out.println("DEBUG: [BonusInput] Team=" + team + ", Input='" + sentence + "'");
        System.out.println("DEBUG: [BonusInput] Available: " + bonusSentences);

        boolean success = false;
        String matchedSentence = null;

        // 정규화 및 비교
        String normalizedInput = java.text.Normalizer.normalize(sentence.trim(), java.text.Normalizer.Form.NFC);

        for (String s : bonusSentences) {
            String normalizedTarget = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFC);
            if (normalizedInput.equals(normalizedTarget)) {
                matchedSentence = s;
                break;
            }
        }

        if (matchedSentence != null) {
            bonusSentences.remove(matchedSentence);
            gameModel.addScore(team, 500);
            success = true;
            System.out.println("서버: 방[" + roomName + "] " + team + "팀이 문장 맞춤! +500점");
        } else {
            System.out.println("DEBUG: [BonusInput] No match found.");
        }

        // 결과 브로드캐스트
        String sentenceToSend = success ? matchedSentence : sentence;
        broadcast(new NetworkProtocol.Msg_S2C_BonusSentenceResult(success, sentenceToSend, team));
    }

    /**
     * SPECIAL 칸 생성 (보너스 타임 시작 시)
     * 
     * [로직]
     * - 보드에서 랜덤하게 3개 위치 선택
     * - GameModel.spawnSpecial() 호출하여 SPECIAL 칸으로 변환
     * - CellUpdate 메시지로 클라이언트에 전달 (Board 전체를 보내지 않고 변경된 칸만 전송)
     */
    public synchronized void spawnSpecialItem() {
        Board board = gameModel.board();
        int rows = board.rows();
        int cols = board.cols();

        // 랜덤 위치 3개 선택
        java.util.Random rnd = new java.util.Random();
        int specialCount = 1;
        for (int i = 0; i < specialCount; i++) {
            int r = rnd.nextInt(rows);
            int c = rnd.nextInt(cols);
            Pos pos = new Pos(r, c);

            Cell cell = board.get(r, c);
            String token = cell.token();

            gameModel.spawnSpecial(pos);

            // 클라이언트에게 변경 알림 (효율적)
            broadcast(new NetworkProtocol.Msg_S2C_CellUpdate(pos, Team.SPECIAL, token));
        }

        System.out.println("서버: 방[" + roomName + "] SPECIAL 칸 " + specialCount + "개 생성.");
    }
}
