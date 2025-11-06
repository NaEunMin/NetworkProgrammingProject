import javax.swing.*;
import java.awt.Color;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * 게임 클라이언트 (Swing 애플리케이션)
 * 1. 서버 접속 후 LobbyFrame(로비)를 띄움.
 * 2. 별도 스레드로 서버 메시지를 리스닝.
 * 3. 서버로부터 GameStart 메시지를 받으면 LobbyFrame을 숨기고 GameFrame을 띄움.
 * 4. 게임 종료/중단 시 GameFrame을 닫고 LobbyFrame을 다시 보여줌.
 */
public class GameClient {

    private static final int PORT = 12345;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    
    // (중요) UI 분리
    private LobbyFrame lobbyFrame; // 메인 로비 UI
    private GameFrame gameFrame;   // 실제 게임 UI
    
    private GameModel localModel;  // 서버와 동기화될 로컬 모델

    public void start() {
        String ip = JOptionPane.showInputDialog(null, "서버 IP 주소를 입력하세요:", "localhost");
        if (ip == null || ip.isBlank()) return;

        try {
            socket = new Socket(ip, PORT);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            System.out.println("클라이언트: 서버에 접속 성공.");

            // 1. (중요) 로비 UI 먼저 생성
            SwingUtilities.invokeLater(() -> {
                lobbyFrame = new LobbyFrame(this);
                lobbyFrame.setVisible(true);
            });

            // 2. 서버 메시지 리스닝 스레드 시작
            new Thread(this::listenToServer).start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "서버 접속 실패: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 서버로부터 오는 메시지를 계속 리스닝 */
    private void listenToServer() {
        try {
            while (socket != null && !socket.isClosed()) {
                Object msg = ois.readObject(); // 서버 메시지 대기

                // (중요) UI 갱신은 반드시 Swing EDT에서 수행
                
                // --- 로비 관련 메시지 ---
                if (msg instanceof NetworkProtocol.Msg_S2C_RoomCreatedWaiting m) {
                    SwingUtilities.invokeLater(() -> 
                        lobbyFrame.setStatus("방 [" + m.roomName() + "] 생성됨. 상대방 대기 중...", Color.CYAN));
                
                } else if (msg instanceof NetworkProtocol.Msg_S2C_RoomResponseFailure m) {
                    SwingUtilities.invokeLater(() -> 
                        lobbyFrame.setStatus(m.reason(), Color.RED));
                    // 간단히 팝업으로도 알림
                    JOptionPane.showMessageDialog(lobbyFrame, m.reason(), "오류", JOptionPane.ERROR_MESSAGE);
                
                } else if (msg instanceof NetworkProtocol.Msg_S2C_ReturnToLobby) {
                    handleReturnToLobby("게임 종료. 로비로 복귀합니다.");

                } else if (msg instanceof NetworkProtocol.Msg_S2C_OpponentLeft) {
                    JOptionPane.showMessageDialog(gameFrame, "상대방이 나갔습니다. 로비로 복귀합니다.", "게임 중단", JOptionPane.WARNING_MESSAGE);
                    handleReturnToLobby("상대방이 나갔습니다.");
                
                
                // --- 게임 시작/진행 메시지 ---
                } else if (msg instanceof NetworkProtocol.Msg_S2C_GameStart m) {
                    // 게임 시작!
                    initializeGame(m.assignedTeam(), m.board(), m.secondsLeft());
                
                } else if (gameFrame != null) {
                    // (게임이 시작된 상태에서만 아래 메시지 처리)
                    if (msg instanceof NetworkProtocol.Msg_S2C_BroadcastInput m) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteInput(m.team(), m.input()));
                    
                    } else if (msg instanceof NetworkProtocol.Msg_S2C_Tick) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteTick());
                    
                    } else if (msg instanceof NetworkProtocol.Msg_S2C_GameOver) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteGameOver());
                        // 잠시 후 로비로 복귀
                        Thread.sleep(3000); // 결과창 볼 시간
                        sendMessage(new NetworkProtocol.Msg_C2S_LeaveRoom()); // 서버에 방 나간다고 알림
                        handleReturnToLobby("게임 종료. 로비로 복귀.");
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("클라이언트: 서버와 연결이 끊어졌습니다.");
            if (gameFrame != null) gameFrame.dispose();
            if (lobbyFrame != null) lobbyFrame.dispose();
            JOptionPane.showMessageDialog(null, "서버와 연결이 끊겼습니다.", "연결 오류", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }
    
    /** (S2C) 게임 시작 메시지 수신 시 호출 */
    private void initializeGame(Team myTeam, Board board, int seconds) {
        // 1. 로컬 TokenIndex 구축
        TokenIndex localIndex = new TokenIndex();
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                Cell cell = board.get(r, c);
                localIndex.add(cell.owner(), cell.token(), new Pos(r, c));
            }
        }
        
        // 2. 로컬 GameModel 생성
        localModel = new GameModel(board, localIndex, seconds, 1);

        // 3. UI 교체 (로비 숨기고 -> 게임창 띄우기)
        SwingUtilities.invokeLater(() -> {
            if (lobbyFrame != null) {
                lobbyFrame.setVisible(false);
            }
            gameFrame = new GameFrame(localModel, this, myTeam);
            gameFrame.setVisible(true);
        });
    }
    
    /** (S2C) 게임 종료/중단 시 로비로 복귀 */
    private void handleReturnToLobby(String message) {
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) {
                gameFrame.dispose(); // 게임창 닫기
                gameFrame = null;
                localModel = null;
            }
            if (lobbyFrame != null) {
                lobbyFrame.setVisible(true); // 로비창 다시 보이기
                lobbyFrame.setStatus(message, Color.WHITE);
            }
        });
    }

    /** (C2S) 서버로 메시지 전송 (범용) */
    private void sendMessage(Serializable message) {
        try {
            if (oos != null) {
                oos.writeObject(message);
                oos.flush();
            }
        } catch (IOException e) {
            System.err.println("클라이언트: 메시지 전송 오류 - " + e.getMessage());
        }
    }
    
    // --- UI가 호출하는 메소드 ---

    /** (C2S) (CreateRoomDialog) 방 생성 요청 */
    public void sendCreateRoomRequest(String roomName, String password, int seconds, Team team) {
        sendMessage(new NetworkProtocol.Msg_C2S_CreateRoom(roomName, password, seconds, team));
        lobbyFrame.setStatus("방 생성 요청 중...", Color.GRAY);
    }
    
    /** (C2S) (JoinRoomDialog) 방 참여 요청 */
    public void sendJoinRoomRequest(String roomName, String password) {
        sendMessage(new NetworkProtocol.Msg_C2S_JoinRoom(roomName, password));
        lobbyFrame.setStatus("방 참여 요청 중...", Color.GRAY);
    }

    /** (C2S) (GameFrame) 단어 입력 요청 */
    public void sendInputRequest(Team team, String input) {
        sendMessage(new NetworkProtocol.Msg_C2S_InputRequest(team, input));
    }
    
    /** (C2S) (GameFrame) 게임방 나가기 (X버튼) */
    public void disconnectFromGame() {
        sendMessage(new NetworkProtocol.Msg_C2S_LeaveRoom());
        handleReturnToLobby("게임방을 나갔습니다.");
    }

    /** 클라이언트 종료 */
    public void stop() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { /* 무시 */ }
        System.out.println("클라이언트: 접속을 종료합니다.");
        System.exit(0); // (LobbyFrame이나 GameFrame이 떠있을 수 있으므로 강제 종료)
    }

    // --- 메인 메소드 ---
    public static void main(String[] args) {
        // (GameLauncher.java 대체)
        GameClient client = new GameClient();
        client.start();
    }
}