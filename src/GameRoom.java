import java.io.Serializable;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 서버 측에서 개별 게임방의 상태를 관리하는 클래스
 */
public class GameRoom {
    
    private final String roomName;
    private final String password;
    private final GameModel gameModel;
    private final GameServer server;
    private final int maxPlayers = 2;

    private ClientHandler playerYellow;
    private ClientHandler playerBlue;
    private ClientHandler owner; // 방장
    private final java.util.Map<ClientHandler, Boolean> readyStates = new java.util.HashMap<>();
    
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

    /** 플레이어를 방에 추가 (방장 포함 참여) */
    public synchronized boolean addPlayer(ClientHandler player, Team team) {
        if (team == Team.YELLOW && playerYellow == null) {
            playerYellow = player;
            player.setCurrentRoom(this);
            if (owner == null) owner = player;
            readyStates.put(player, false);
            return true;
        } else if (team == Team.BLUE && playerBlue == null) {
            playerBlue = player;
            player.setCurrentRoom(this);
            if (owner == null) owner = player;
            readyStates.put(player, false);
            return true;
        }
        return false; // 해당 팀이 찼음
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
        readyStates.remove(player);

        if (owner == player) {
            owner = opponent; // 남아있는 플레이어에게 방장 위임
        }

        if (isPlaying && opponent != null) {
            // 게임 중에 한명이 퇴장
            opponent.sendMessage(new NetworkProtocol.Msg_S2C_OpponentLeft());
            stopGame(); // 타이머 중단
        } else if (!isPlaying) {
            broadcastPlayerList(); // 대기방이면 목록 갱신
        }
        
        // 방이 비었으면 서버에서 제거
        if (playerYellow == null && playerBlue == null) {
            System.out.println("서버: 방[" + roomName + "]이 비어 제거됩니다.");
            server.removeRoom(this);
        } else {
            server.broadcastRoomUpdated(this);
        }
    }

    /** 방이 꽉 찼는지 확인 */
    public synchronized boolean isFull() {
        return playerYellow != null && playerBlue != null;
    }
    
    /** 반대 팀 자리 반환 */
    public synchronized Team getOppositeTeam(Team team) {
        if (team == Team.YELLOW) {
            return (playerBlue == null) ? Team.BLUE : null; // 파랑팀이 비었으면 파랑팀 반환
        } else {
            return (playerYellow == null) ? Team.YELLOW : null; // 노랑팀이 비었으면 노랑팀 반환
        }
    }

    /** 현재 인원 반환 */
    public synchronized int getPlayerCount() {
        int cnt = 0;
        if (playerYellow != null) cnt++;
        if (playerBlue != null) cnt++;
        return cnt;
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    public synchronized NetworkProtocol.RoomInfo toRoomInfo() {
        return new NetworkProtocol.RoomInfo(roomName, gameModel.secondsLeft(), getPlayerCount(), maxPlayers, isPlaying);
    }

    public synchronized java.util.List<NetworkProtocol.PlayerInfo> snapshotPlayers() {
        ArrayList<NetworkProtocol.PlayerInfo> list = new ArrayList<>();
        if (playerYellow != null) list.add(new NetworkProtocol.PlayerInfo(playerYellow.getNickname(), Team.YELLOW, readyStates.getOrDefault(playerYellow, false), owner == playerYellow));
        if (playerBlue != null) list.add(new NetworkProtocol.PlayerInfo(playerBlue.getNickname(), Team.BLUE, readyStates.getOrDefault(playerBlue, false), owner == playerBlue));
        return list;
    }
    
    /** 2명이 모여 게임 시작 */
    public synchronized void startGameBy(ClientHandler requester) {
        if (!isFull() || isPlaying) return;
        if (owner != null && requester != owner) {
            requester.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("방장만 게임을 시작할 수 있습니다."));
            return;
        }
        // 두 명 모두 준비 상태인지 확인
        if (!readyStates.getOrDefault(playerYellow, false) || !readyStates.getOrDefault(playerBlue, false)) {
            requester.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("두 플레이어 모두 준비해야 시작할 수 있습니다."));
            return;
        }
        
        isPlaying = true;
        System.out.println("서버: 방[" + roomName + "] 게임 시작.");

        // 양쪽 클라이언트에게 게임 시작 알림 송신
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

        server.broadcastRoomUpdated(this);
    }
    
    /** 게임 중단 (시간 종료 또는 플레이어 이탈) */
    public synchronized void stopGame() {
        if (!isPlaying) return;
        
        isPlaying = false;
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        System.out.println("서버: 방[" + roomName + "] 게임 종료.");
        broadcast(new NetworkProtocol.Msg_S2C_GameOver());
        server.broadcastRoomUpdated(this);
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

    /** 대기방 플레이어 목록 브로드캐스트 */
    public synchronized void broadcastPlayerList() {
        var msg = new NetworkProtocol.Msg_S2C_PlayerListUpdated(snapshotPlayers());
        broadcast(msg);
    }

    /** 준비 상태 변경 */
    public synchronized void setReady(ClientHandler player, boolean ready) {
        if (!readyStates.containsKey(player)) return;
        readyStates.put(player, ready);
        broadcastPlayerList();
    }
    
    /** 클라이언트의 입력 요청 처리 */
    public synchronized void handleInput(ClientHandler player, Team team, String input) {
        if (!isPlaying) return;
        
        // (중요) 서버의 GameModel을 먼저 업데이트
        var flips = gameModel.flipByInput(team, input);
        
        // (중요) 입력이 유효했는지(0) 여부와 관계없어도
        // 모든 클라이언트에 동일한 입력을 처리하도록 브로드캐스트 (Lock-step)
        // (만약 최적화한다면 flipped > 0 일때만 보내도 됨)
        broadcast(new NetworkProtocol.Msg_S2C_BroadcastInput(team, input));
    }

    /** 대기방 채팅 브로드캐스트 */
    public synchronized void broadcastWaitingChat(String sender, String text) {
        broadcast(new NetworkProtocol.Msg_S2C_WaitingChat(sender, text));
    }
}
