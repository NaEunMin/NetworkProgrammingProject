import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임 서버 (간단 싱글 프로세스)
 * 1. 소켓 서버: 다수 클라이언트 연결을 받고, ClientHandler 스레드에 할당.
 * 2. 방 관리: "activeRooms" 맵을 통해 여러 GameRoom 인스턴스를 관리.
 * 3. 메시지 처리: 클라이언트의 방 생성/참여/대기/게임 요청을 처리.
 */
public class GameServer {

    private static final int PORT = 12345;
    private static final int ROWS = 8;
    private static final int COLS = 12;

    // 방 이름(String) -> GameRoom
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // 로비에 있는 클라이언트 목록
    private final Set<ClientHandler> lobbyClients = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("서버: " + PORT + " 포트에서 클라이언트 대기 중...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("서버: 클라이언트 연결: " + socket.getRemoteSocketAddress());
                
                ClientHandler handler = new ClientHandler(socket, this);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("서버: 오류 발생 - " + e.getMessage());
        }
    }

    // --- 방 생성/참여/삭제 ---

    /** 클라이언트의 방 생성 요청 처리 */
    public synchronized void handleCreateRoom(ClientHandler creator, String roomName, String password, int gameTimeSec, Team chosenTeam) {
        if (activeRooms.containsKey(roomName)) {
            creator.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("이미 존재하는 방 이름입니다."));
            return;
        }

        System.out.println("서버: " + creator.getNickname() + "이(가) 방 생성 시도: [" + roomName + "]");
        
        Board board = new Board(ROWS, COLS);
        TokenIndex index = new TokenIndex();
        fillBoardFromFilesOrFallback(board, index);
        GameModel gameModel = new GameModel(board, index, gameTimeSec, 1, WordPool.fromBoard(board));

        GameRoom newRoom = new GameRoom(roomName, password, gameModel, this);
        newRoom.addPlayer(creator, chosenTeam);
        
        activeRooms.put(roomName, newRoom);
        
        sendEnterWaitingRoom(creator, newRoom, chosenTeam);
        broadcastRoomUpdated(newRoom);
    }

    /** 클라이언트의 방 참여 요청 처리 */
    public synchronized void handleJoinRoom(ClientHandler joiner, String roomName, String password) {
        GameRoom room = activeRooms.get(roomName);

        if (room == null) {
            joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("존재하지 않는 방입니다."));
            return;
        }
        
        if (!room.getPassword().isEmpty() && !room.getPassword().equals(password)) {
            joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("비밀번호가 다릅니다."));
            return;
        }

        Team teamToJoin = room.getOppositeTeam(Team.BLUE);
        if (teamToJoin == null) {
            teamToJoin = room.getOppositeTeam(Team.YELLOW);
        }
        
        if (teamToJoin == null || !room.addPlayer(joiner, teamToJoin)) {
             joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("참여할 자리가 없거나 입장에 실패했습니다."));
             return;
        }
        
        System.out.println("서버: " + joiner.getNickname() + "이(가) 방 [" + roomName + "]에 " + teamToJoin + "팀으로 참여.");

        sendEnterWaitingRoom(joiner, room, teamToJoin);
        room.broadcastPlayerList();
        broadcastRoomUpdated(room);
    }

    /** 방 제거 */
    public synchronized void removeRoom(GameRoom room) {
        activeRooms.remove(room.getRoomName());
        broadcastRoomRemoved(room.getRoomName());
    }

    // --- 로비 브로드캐스트 ---

    public void registerLobbyClient(ClientHandler client) {
        lobbyClients.add(client);
        sendRoomList(client);
    }

    public void unregisterLobbyClient(ClientHandler client) {
        lobbyClients.remove(client);
    }

    public void sendRoomList(ClientHandler target) {
        List<NetworkProtocol.RoomInfo> infos = activeRooms.values().stream()
                .map(GameRoom::toRoomInfo)
                .toList();
        target.sendMessage(new NetworkProtocol.Msg_S2C_RoomList(infos));
    }

    public void broadcastRoomUpdated(GameRoom room) {
        NetworkProtocol.RoomInfo info = room.toRoomInfo();
        var msg = new NetworkProtocol.Msg_S2C_RoomUpdated(info);
        for (ClientHandler client : lobbyClients) {
            client.sendMessage(msg);
        }
    }

    public void broadcastRoomRemoved(String roomName) {
        var msg = new NetworkProtocol.Msg_S2C_RoomRemoved(roomName);
        for (ClientHandler client : lobbyClients) {
            client.sendMessage(msg);
        }
    }

    // --- 유틸 ---

    private void sendEnterWaitingRoom(ClientHandler client, GameRoom room, Team myTeam) {
        var msg = new NetworkProtocol.Msg_S2C_EnterWaitingRoom(room.toRoomInfo(), room.snapshotPlayers(), myTeam);
        client.sendMessage(msg);
    }

    /** 기본 단어 세팅 (resources/word.txt가 있으면 랜덤 채움) */
    private void fillBoardFromFilesOrFallback(Board board, TokenIndex idx) {
        List<String> fallback = List.of("\uac10\uc790", "\uc0ac\uacfc", "\ud3ec\ub3c4", "\uc218\ubc15", "\ucf54\ucf54", "\ud638\ub791\uc774", "\uacf0\ub3cc", "\uc5ec\uc6b0", "\ub291\ub300", "\ud1a0\ub07c");

        Path wordPath = Path.of("resources", "word.txt");
        List<String> words = readOrFallback(wordPath, fallback);

        int maxLen = 8; // 너무 긴 단어는 겹침 방지를 위해 제외
        List<String> filtered = new ArrayList<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= maxLen) {
                filtered.add(trimmed);
            }
        }
        if (filtered.isEmpty()) filtered = fallback;

        int totalCells = board.rows() * board.cols();
        List<String> pool = new ArrayList<>(filtered);
        while (pool.size() < totalCells) {
            pool.addAll(filtered);
        }

        Random rnd = new Random();
        Collections.shuffle(pool, rnd);

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

    private static List<String> readOrFallback(Path path, List<String> fallback) {
        try {
            if (Files.exists(path)) {
                var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                List<String> words = lines.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
                System.out.println("서버: " + path + "에서 단어 " + words.size() + "개 불러옴.");
                return words.isEmpty() ? fallback : words;
            } else {
                System.out.println("서버: " + path + " 파일을 찾지 못해 기본 단어를 사용합니다.");
            }
        } catch (Exception e) {
            System.err.println("서버: " + path + " 파일 읽기 실패: " + e.getMessage());
        }
        return fallback;
    }
    
    public static void main(String[] args) {
        new GameServer().start();
    }
}


/**
 * (GameServer 클래스 바로 아래 두거나 별도 파일로 이동)
 * 한 클라이언트와 통신을 담당하는 클래스
 */
class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    
    private GameRoom currentRoom = null; // 현재 방
    public final String id; // 연결된 ID
    private String nickname = "Player";

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.id = socket.getRemoteSocketAddress().toString();
    }
    
    public void setCurrentRoom(GameRoom room) {
        this.currentRoom = room;
    }

    public GameRoom getCurrentRoom() {
        return currentRoom;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public void run() {
        try {
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Object msg = ois.readObject();

                // --- 로비/대기 메시지 ---
                if (msg instanceof NetworkProtocol.Msg_C2S_Handshake req) {
                    this.nickname = req.nickname();
                    server.registerLobbyClient(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_RequestRoomList) {
                    server.sendRoomList(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_CreateRoom req) {
                    server.handleCreateRoom(this, req.roomName(), req.password(), req.gameTimeSec(), req.chosenTeam());
                
                } else if (msg instanceof NetworkProtocol.Msg_C2S_JoinRoom req) {
                    server.handleJoinRoom(this, req.roomName(), req.password());

                // --- 대기/게임 중 메시지 ---
                } else if (currentRoom != null) {
                    
                    if (msg instanceof NetworkProtocol.Msg_C2S_InputRequest req) {
                        currentRoom.handleInput(this, req.team(), req.input());
                    
                    } else if (msg instanceof NetworkProtocol.Msg_C2S_LeaveRoom) {
                        currentRoom.removePlayer(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_StartGame) {
                    currentRoom.startGameBy(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_ToggleReady reqReady) {
                    currentRoom.setReady(this, reqReady.ready());

                } else if (msg instanceof NetworkProtocol.Msg_C2S_WaitingChat reqChat) {
                    currentRoom.broadcastWaitingChat(nickname, reqChat.text());
                }
            }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("서버: 클라이언트[" + id + "] 연결 종료.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버: 클라이언트[" + id + "] 스레드 오류 - " + e.getMessage());
        } finally {
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
            }
            server.unregisterLobbyClient(this);
            try { socket.close(); } catch (IOException e) {}
        }
    }

    /** 특정 클라이언트에게 메시지 전송 */
    public void sendMessage(Serializable message) {
        try {
            if (oos != null) {
                oos.writeObject(message);
                oos.flush();
                oos.reset(); 
            }
        } catch (IOException e) {
            System.err.println("서버: [" + id + "] 메시지 전송 오류 - " + e.getMessage());
        }
    }
}