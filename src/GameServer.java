import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 게임 서버 (콘솔 애플리케이션)
 * 1. 로비 역할: 다수의 클라이언트 접속을 받고, ClientHandler 스레드 할당.
 * 2. 방 관리: "activeRooms" 맵을 통해 다수의 GameRoom 인스턴스를 관리.
 * 3. 메시지 처리: 클라이언트의 방 생성/참여 요청을 처리.
 */
public class GameServer {

    private static final int PORT = 12345;
    private static final int ROWS = 8;
    private static final int COLS = 12;

    // (중요) 방 이름(String)을 키로 GameRoom을 관리
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("서버: " + PORT + " 포트에서 클라이언트 접속 대기 중...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("서버: 클라이언트 접속: " + socket.getRemoteSocketAddress());
                
                // 클라이언트 핸들러 생성 및 스레드 시작
                ClientHandler handler = new ClientHandler(socket, this);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("서버: 오류 발생 - " + e.getMessage());
        }
    }

    // --- 방 관리 로직 ---

    /** 클라이언트의 방 생성 요청 처리 */
    public synchronized void handleCreateRoom(ClientHandler creator, String roomName, String password, int gameTimeSec, Team chosenTeam) {
        if (activeRooms.containsKey(roomName)) {
            // 이미 방이 존재
            creator.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("이미 존재하는 방 이름입니다."));
            return;
        }

        System.out.println("서버: " + creator.id + "가 방 생성 시도: [" + roomName + "]");
        
        // 1. 새 게임을 위한 보드와 모델 생성
        Board board = new Board(ROWS, COLS);
        TokenIndex index = new TokenIndex();
        fillBoardFromFilesOrFallback(board, index); // 단어 파일 로드
        GameModel gameModel = new GameModel(board, index, gameTimeSec, 1);

        // 2. 새 게임방 생성
        GameRoom newRoom = new GameRoom(roomName, password, gameModel, this);
        
        // 3. 방장을 방에 추가
        newRoom.addPlayer(creator, chosenTeam);
        
        // 4. 서버의 방 목록에 추가
        activeRooms.put(roomName, newRoom);
        
        // 5. 방장에게 "대기 중" 메시지 전송
        creator.sendMessage(new NetworkProtocol.Msg_S2C_RoomCreatedWaiting(roomName));
        System.out.println("서버: 방 [" + roomName + "] 생성됨. " + chosenTeam + "팀 대기 중.");
    }

    /** 클라이언트의 방 참여 요청 처리 */
    public synchronized void handleJoinRoom(ClientHandler joiner, String roomName, String password) {
        GameRoom room = activeRooms.get(roomName);

        // 1. 방 존재 여부
        if (room == null) {
            joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("존재하지 않는 방입니다."));
            return;
        }
        
        // 2. 비밀번호 확인
        if (!room.getPassword().isEmpty() && !room.getPassword().equals(password)) {
            joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("비밀번호가 틀렸습니다."));
            return;
        }

        // 3. 빈 팀 확인 및 참여
        // (방장이 노랑팀을 골랐으면, 파랑팀 자리가 비었는지 확인)
        Team teamToJoin = room.getOppositeTeam(Team.BLUE); // 노랑팀 자리 비었나?
        if (teamToJoin == null) {
             teamToJoin = room.getOppositeTeam(Team.YELLOW); // 파랑팀 자리 비었나?
        }
        
        if (teamToJoin == null || !room.addPlayer(joiner, teamToJoin)) {
             joiner.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("방이 꽉 찼거나 참여할 수 없습니다."));
             return;
        }
        
        System.out.println("서버: " + joiner.id + "가 방 [" + roomName + "]에 " + teamToJoin + "팀으로 참여.");

        // 4. 방이 꽉 찼으므로 게임 시작
        room.startGame();
    }
    
    /** 방 목록에서 방 제거 (방이 비었을 때) */
    public void removeRoom(GameRoom room) {
        activeRooms.remove(room.getRoomName());
    }

    // --- (GameServer의 기존 코드) ---
    private static void fillBoardFromFilesOrFallback(Board board, TokenIndex idx) {
        System.out.println("서버: 단어 파일 로드를 시도합니다. (words/yellow.txt, words/blue.txt)");
        
        var yTokens = readOrFallback(Path.of("words", "yellow.txt"),
                List.of("감사합니다","기운","아침","바닷길","단어게임","풍덩이다","친구들","도깨비","미래","부엌","사과","바다","연구","우리"));
        var bTokens = readOrFallback(Path.of("words", "blue.txt"),
                List.of("연필","지우개","공책","자","필통","가방","학교","운동장","교실","칠판","분필","연습장","책상","의자"));

        int rows = board.rows(), cols = board.cols();
        int yi = 0, bi = 0;
        for (int r=0; r<rows; r++) {
            for (int c=0; c<cols; c++) {
                boolean yellow = (r < rows/2);
                Team owner = yellow ? Team.YELLOW : Team.BLUE;
                String token = yellow
                        ? yTokens.get(yi++ % yTokens.size())
                        : bTokens.get(bi++ % bTokens.size());
                Cell cell = new Cell(owner, token);
                board.set(r, c, cell);
                idx.add(owner, token, new Pos(r, c));
            }
        }
    }

    private static List<String> readOrFallback(Path path, List<String> fallback) {
        try {
            if (Files.exists(path)) {
                var lines = Files.readAllLines(path);
                List<String> words = lines.stream().map(String::trim).filter(s->!s.isEmpty()).toList();
                System.out.println("서버: " + path + "에서 단어 " + words.size() + "개 로드 성공.");
                return words.isEmpty() ? fallback : words;
            } else {
                System.out.println("서버: " + path + " 파일을 찾을 수 없어 기본 단어를 사용합니다.");
            }
        } catch (Exception e) {
            System.err.println("서버: " + path + " 파일 읽기 오류: " + e.getMessage());
        }
        return fallback;
    }
    
    public static void main(String[] args) {
        new GameServer().start();
    }
}


/**
 * (GameServer 파일 내부에 두거나 별도 파일로 분리)
 * 각 클라이언트와의 통신을 담당하는 스레드
 */
class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    
    private GameRoom currentRoom = null; // (중요) 현재 이 클라이언트가 속한 방
    public final String id; // 디버깅용 ID

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.id = socket.getRemoteSocketAddress().toString();
    }
    
    public void setCurrentRoom(GameRoom room) {
        this.currentRoom = room;
    }

    @Override
    public void run() {
        try {
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            // 클라이언트로부터 오는 메시지(로비/게임)를 계속 리스닝
            while (true) {
                Object msg = ois.readObject();

                // 1. 로비 메시지 처리
                if (msg instanceof NetworkProtocol.Msg_C2S_CreateRoom req) {
                    server.handleCreateRoom(this, req.roomName(), req.password(), req.gameTimeSec(), req.chosenTeam());
                
                } else if (msg instanceof NetworkProtocol.Msg_C2S_JoinRoom req) {
                    server.handleJoinRoom(this, req.roomName(), req.password());

                // 2. 게임 중 메시지 처리 (반드시 currentRoom이 null이 아닐 때)
                } else if (currentRoom != null) {
                    
                    if (msg instanceof NetworkProtocol.Msg_C2S_InputRequest req) {
                        currentRoom.handleInput(this, req.team(), req.input());
                    
                    } else if (msg instanceof NetworkProtocol.Msg_C2S_LeaveRoom) {
                        currentRoom.removePlayer(this);
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("서버: 클라이언트[" + id + "] 접속 종료.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버: 클라이언트[" + id + "] 핸들러 오류 - " + e.getMessage());
        } finally {
            // (중요) 클라이언트 접속이 끊어지면, 속해있던 방에서 제거
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
            }
            try { socket.close(); } catch (IOException e) {}
        }
    }

    /** 이 클라이언트에게 메시지 전송 */
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