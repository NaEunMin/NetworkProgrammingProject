package server;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import protocol.*;
import util.*;
import game.Board;
import game.GameModel;
import game.TokenIndex;
import game.Team;
import game.Cell;
import game.Pos;

/**
 * 멀티플레이 게임 서버
 * 
 * [설계]
 * - TCP ServerSocket으로 다수 클라이언트 동시 연결 처리
 * - 각 클라이언트를 별도 스레드(ClientHandler)에서 처리
 * - ConcurrentHashMap으로 방 목록 관리 (스레드 안전)
 * - 방(GameRoom) 단위로 게임 진행, 각 방은 독립적으로 타이머/보너스 시간 관리
 * 
 * [핵심 기능]
 * 1. 연결 관리: 클라이언트 접속 수락 및 ClientHandler 생성
 * 2. 방 관리: 방 생성/삭제/참여 처리
 * 3. 로비 브로드캐스트: 로비에 있는 클라이언트들에게 방 목록 갱신 알림
 */
public class GameServer {

    private static final int PORT = 12345;
    private static final int ROWS = 8;
    private static final int COLS = 12;

    // 방 관리 (ConcurrentHashMap으로 멀티스레드 환경에서 안전성 보장)
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    
    // 로비에 있는 클라이언트 목록 (방 목록 갱신 브로드캐스트 대상)
    private final Set<ClientHandler> lobbyClients = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 보너스 타임 문장 풀 (resources/text.txt에서 로드)
    private final SentencePool sentencePool;

    public GameServer() {
        this.sentencePool = SentencePool.fromFile("resources/text.txt");
    }

    /**
     * 서버 시작 (무한 루프로 클라이언트 연결 대기)
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("서버: " + PORT + " 포트에서 클라이언트 대기 중...");

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler client = new ClientHandler(socket, this);
                new Thread(client).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 새로운 게임 모델 생성 (방 생성 시 호출)
     * 보드를 초기화하고 word.txt에서 단어를 로드하여 배치
     */
    public GameModel createGameModel(int gameTimeSec) {
        Board board = new Board(ROWS, COLS);
        TokenIndex index = new TokenIndex();
        fillBoardFromFilesOrFallback(board, index);
        return new GameModel(board, index, gameTimeSec, 1, WordPool.fromBoard(board));
    }

    /**
     * 방 생성 요청 처리
     * 
     * [처리 과정]
     * 1. 중복 방 이름 검사
     * 2. GameModel 생성 (보드 초기화)
     * 3. GameRoom 객체 생성
     * 4. 방장을 선택한 팀으로 입장
     * 5. activeRooms에 등록
     * 6. 로비에 방 목록 갱신 브로드캐스트
     */
    public synchronized void handleCreateRoom(ClientHandler creator, String roomName, String password, int gameTimeSec,
            boolean bonusEnabled, NetworkProtocol.Theme theme, Team chosenTeam) {
        if (activeRooms.containsKey(roomName)) {
            creator.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("이미 존재하는 방 이름입니다."));
            return;
        }

        // 게임 모델 생성
        GameModel model = createGameModel(gameTimeSec);

        // 방 생성
        GameRoom newRoom = new GameRoom(roomName, password, model, gameTimeSec, bonusEnabled, theme, this,
                sentencePool);

        // 방장 입장 (선택한 팀으로)
        if (!newRoom.addPlayer(creator, chosenTeam)) {
            creator.sendMessage(new NetworkProtocol.Msg_S2C_RoomResponseFailure("방 생성 후 입장 실패"));
            return;
        }

        activeRooms.put(roomName, newRoom);
        System.out.println("서버: 방 생성 [" + roomName + "] (테마: " + theme + ", 보너스: " + bonusEnabled + ")");

        // 방장에게 대기방 입장 알림
        sendEnterWaitingRoom(creator, newRoom, chosenTeam);

        // 로비에 방 목록 갱신 알림
        broadcastRoomUpdated(newRoom);
    }

    /**
     * 방 참여 요청 처리
     * 
     * [처리 과정]
     * 1. 방 존재 여부 확인
     * 2. 비밀번호 검증
     * 3. 빈 팀 찾기 (상대방이 없는 팀)
     * 4. 입장 처리
     * 5. 대기방 플레이어 목록 갱신
     */
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

        // 빈 팀 찾기 (BLUE 먼저, 없으면 YELLOW)
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

    /**
     * 방 제거 (게임 종료 또는 모든 플레이어가 나간 경우)
     */
    public synchronized void removeRoom(GameRoom room) {
        activeRooms.remove(room.getRoomName());
        broadcastRoomRemoved(room.getRoomName());
    }

    // ===== 로비 브로드캐스트 =====

    /**
     * 로비에 클라이언트 등록 (첫 연결 시)
     * 등록 시 현재 방 목록 전송
     */
    public void registerLobbyClient(ClientHandler client) {
        lobbyClients.add(client);
        sendRoomList(client);
    }

    public void unregisterLobbyClient(ClientHandler client) {
        lobbyClients.remove(client);
    }

    /**
     * 특정 클라이언트에게 방 목록 전송
     */
    public void sendRoomList(ClientHandler target) {
        List<NetworkProtocol.RoomInfo> infos = activeRooms.values().stream()
                .map(GameRoom::toRoomInfo)
                .toList();
        target.sendMessage(new NetworkProtocol.Msg_S2C_RoomList(infos));
    }

    /**
     * 로비의 모든 클라이언트에게 방 정보 갱신 브로드캐스트
     * (방 생성, 플레이어 입장/퇴장, 게임 시작 등)
     */
    public void broadcastRoomUpdated(GameRoom room) {
        NetworkProtocol.RoomInfo info = room.toRoomInfo();
        var msg = new NetworkProtocol.Msg_S2C_RoomUpdated(info);
        for (ClientHandler client : lobbyClients) {
            client.sendMessage(msg);
        }
    }

    /**
     * 로비의 모든 클라이언트에게 방 삭제 알림
     */
    public void broadcastRoomRemoved(String roomName) {
        var msg = new NetworkProtocol.Msg_S2C_RoomRemoved(roomName);
        for (ClientHandler client : lobbyClients) {
            client.sendMessage(msg);
        }
    }

    // ===== 유틸리티 =====

    private void sendEnterWaitingRoom(ClientHandler client, GameRoom room, Team myTeam) {
        var msg = new NetworkProtocol.Msg_S2C_EnterWaitingRoom(room.toRoomInfo(), room.snapshotPlayers(), myTeam);
        client.sendMessage(msg);
    }

    /**
     * 보드 초기화 (resources/word.txt에서 단어 로드)
     * 
     * [처리 과정]
     * 1. word.txt에서 단어 로드 (실패 시 기본 단어 사용)
     * 2. 4글자 이하로 필터링 (UI 칸 크기 제약)
     * 3. 전체 칸 수만큼 단어 확보 (부족하면 반복)
     * 4. 셔플 후 상반/하반으로 나누어 YELLOW/BLUE 배정
     */
    private void fillBoardFromFilesOrFallback(Board board, TokenIndex idx) {
        List<String> fallback = List.of("감자", "사과", "포도", "수박", "코코",
                "호랑이", "곰돌", "여우", "늑대", "토끼");

        Path wordPath = Path.of("resources", "word.txt");
        List<String> words = readOrFallback(wordPath, fallback);

        int maxLen = 4; // UI 칸 크기에 맞추어 4글자 제한
        List<String> filtered = new ArrayList<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= maxLen) {
                filtered.add(trimmed);
            }
        }
        if (filtered.isEmpty())
            filtered = fallback;

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

    /**
     * 파일에서 단어 로드 (실패 시 fallback 반환)
     */
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
 * 클라이언트 연결 핸들러 (스레드 단위로 실행)
 * 
 * [설계]
 * - 하나의 ClientHandler가 하나의 클라이언트와 1:1 통신
 * - ObjectInputStream/ObjectOutputStream으로 메시지 송수신
 * - 연결이 끊기면 자동으로 방에서 제거 및 로비에서 등록 해제
 * 
 * [메시지 라우팅]
 * - 로비 메시지: Handshake, RequestRoomList, CreateRoom, JoinRoom
 * - 방/게임 메시지: currentRoom이 null이 아닐 때, 방으로 전달
 */
class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;

    private GameRoom currentRoom = null; // 현재 속한 방 (null이면 로비)
    public final String id; // 소켓 주소
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
            // 중요: OutputStream을 먼저 생성해야 헤더 전송 문제 방지
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Object msg = ois.readObject();

                // ===== 로비 메시지 처리 =====
                if (msg instanceof NetworkProtocol.Msg_C2S_Handshake req) {
                    this.nickname = req.nickname();
                    server.registerLobbyClient(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_RequestRoomList) {
                    server.sendRoomList(this);

                } else if (msg instanceof NetworkProtocol.Msg_C2S_CreateRoom req) {
                    server.handleCreateRoom(this, req.roomName(), req.password(), req.gameTimeSec(), req.bonusEnabled(),
                            req.theme(), req.chosenTeam());

                } else if (msg instanceof NetworkProtocol.Msg_C2S_JoinRoom req) {
                    server.handleJoinRoom(this, req.roomName(), req.password());

                // ===== 방/게임 메시지 처리 (currentRoom으로 전달) =====
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
                        
                    } else if (msg instanceof NetworkProtocol.Msg_C2S_SentenceInput req) {
                        currentRoom.handleSentenceInput(this, req.team(), req.sentence());
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("서버: 클라이언트[" + id + "] 연결 종료.");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("서버: 클라이언트[" + id + "] 스레드 오류 - " + e.getMessage());
        } finally {
            // 연결 종료 시 정리
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
            }
            server.unregisterLobbyClient(this);
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * 클라이언트에게 메시지 전송
     * reset() 호출로 메모리 누수 방지 (ObjectOutputStream 내부 캐시 초기화)
     */
    public void sendMessage(Serializable message) {
        try {
            if (oos != null) {
                oos.writeObject(message);
                oos.flush();
                oos.reset(); // 메모리 누수 방지
            }
        } catch (IOException e) {
            System.err.println("서버: [" + id + "] 메시지 전송 오류 - " + e.getMessage());
        }
    }
}