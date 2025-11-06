import java.io.Serializable;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 서버 측에서 개별 게임방의 상태를 관리하는 클래스.
 */
public class GameRoom {
    
    private final String roomName;
    private final String password;
    private final GameModel gameModel;
    private final GameServer server;

    private ClientHandler playerYellow;
    private ClientHandler playerBlue;
    
    private boolean isPlaying = false;
    private Timer gameTimer;

    public GameRoom(String roomName, String password, GameModel gameModel, GameServer server) {
        this.roomName = roomName;
        this.password = password;
        this.gameModel = gameModel;
        this.server = server;
    }

    public String getRoomName() { return roomName; }
    public String getPassword() { return password; }

    /** 플레이어를 방에 추가 (방장이거나, 참여자) */
    public synchronized boolean addPlayer(ClientHandler player, Team team) {
        if (team == Team.YELLOW && playerYellow == null) {
            playerYellow = player;
            player.setCurrentRoom(this);
            return true;
        } else if (team == Team.BLUE && playerBlue == null) {
            playerBlue = player;
            player.setCurrentRoom(this);
            return true;
        }
        return false; // 해당 팀이 이미 찼음
    }
    
    /** 플레이어가 방에서 나갈 때 */
    public synchronized void removePlayer(ClientHandler player) {
        ClientHandler opponent = null;
        if (player == playerYellow) {
            playerYellow = null;
            opponent = playerBlue;
        } else if (player == playerBlue) {
            playerBlue = null;
            opponent = playerYellow;
        }
        
        player.setCurrentRoom(null); // 플레이어의 방 정보 초기화

        if (isPlaying && opponent != null) {
            // 게임 중에 한 명이 나감
            opponent.sendMessage(new NetworkProtocol.Msg_S2C_OpponentLeft());
            stopGame(); // 타이머 중지
        }
        
        // 방이 비었으면 서버에서 제거
        if (playerYellow == null && playerBlue == null) {
            System.out.println("서버: 방 [" + roomName + "]이 비어서 제거됩니다.");
            server.removeRoom(this);
        }
    }

    /** 방이 꽉 찼는지 확인 */
    public synchronized boolean isFull() {
        return playerYellow != null && playerBlue != null;
    }
    
    /** 반대편 팀 반환 */
    public synchronized Team getOppositeTeam(Team team) {
        if (team == Team.YELLOW) {
            return (playerBlue == null) ? Team.BLUE : null; // 파랑팀이 비었으면 파랑팀 반환
        } else {
            return (playerYellow == null) ? Team.YELLOW : null; // 노랑팀이 비었으면 노랑팀 반환
        }
    }
    
    /** 2명이 모여 게임 시작 */
    public synchronized void startGame() {
        if (!isFull() || isPlaying) return;
        
        isPlaying = true;
        System.out.println("서버: 방 [" + roomName + "] 게임 시작.");

        // 양쪽 클라이언트에게 게임 시작 정보 전송
        Board board = gameModel.board();
        int seconds = gameModel.secondsLeft();
        
        playerYellow.sendMessage(new NetworkProtocol.Msg_S2C_GameStart(Team.YELLOW, board, seconds));
        playerBlue.sendMessage(new NetworkProtocol.Msg_S2C_GameStart(Team.BLUE, board, seconds));

        // 서버 타이머 시작
        gameTimer = new Timer();
        gameTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                gameModel.tickOneSecond();
                broadcast(new NetworkProtocol.Msg_S2C_Tick());

                if (gameModel.secondsLeft() <= 0) {
                    stopGame(); // 시간 종료
                }
            }
        }, 1000, 1000);
    }
    
    /** 게임 중지 (시간 종료 또는 플레이어 이탈) */
    public synchronized void stopGame() {
        if (!isPlaying) return;
        
        isPlaying = false;
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        System.out.println("서버: 방 [" + roomName + "] 게임 종료.");
        broadcast(new NetworkProtocol.Msg_S2C_GameOver());
    }

    /** 방에 있는 모든 플레이어에게 메시지 브로드캐스트 */
    public synchronized void broadcast(Serializable message) {
        if (playerYellow != null) {
            playerYellow.sendMessage(message);
        }
        if (playerBlue != null) {
            playerBlue.sendMessage(message);
        }
    }
    
    /** 클라이언트의 입력 요청 처리 */
    public synchronized void handleInput(ClientHandler player, Team team, String input) {
        if (!isPlaying) return;
        
        // (중요) 서버의 GameModel을 먼저 업데이트
        int flipped = gameModel.flipByInput(team, input);
        
        // (중요) 입력이 유효했는지(0) 여부와 관계없이,
        // 모든 클라이언트가 동일한 입력을 처리하도록 브로드캐스트 (Lock-step)
        // (만약 최적화한다면 flipped > 0 일 때만 보내도 됨)
        broadcast(new NetworkProtocol.Msg_S2C_BroadcastInput(team, input));
    }
}