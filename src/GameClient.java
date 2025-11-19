import javax.swing.*;
import java.awt.Color;
import java.awt.Image;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;

/**
 * 게임 클라이언트 (Swing 애플리케이션)
 * 1. 서버 접속 후 LobbyFrame(로비)을 띄운다.
 * 2. 별도 쓰레드로 서버 메시지 리스닝.
 * 3. 서버로부터 GameStart 메시지를 받으면 LobbyFrame을 숨기고 GameFrame을 연다.
 * 4. 게임 종료/중단 시 GameFrame을 닫고 LobbyFrame/대기방으로 복귀.
 */
public class GameClient {

    private static final int PORT = 12345;

    private Socket socket;
    private ObjectOutputStream oos;
    private ObjectInputStream ois;
    private String nickname = "Player";
    
    // UI
    private LobbyFrame lobbyFrame;        // 메인 로비 UI
    private WaitingRoomFrame waitingRoomFrame; // 대기방 UI
    private GameFrame gameFrame;          // 실제 게임 UI
    
    private GameModel localModel;         // 서버와 동기화될 로컬 모델

    public void start() {
        String imagePath = "resources/images/login_background.png";
        String ip = null;
        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = (int) (screen.width * 0.6);
            int maxH = (int) (screen.height * 0.6);

            BufferedImage img = tryLoadImage(imagePath);
            ImageIcon icon = null;
            if (img != null) {
                int w = img.getWidth();
                int h = img.getHeight();
                double scale = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
                if (scale < 1.0) {
                    w = (int) (w * scale);
                    h = (int) (h * scale);
                    Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                    icon = new ImageIcon(scaled);
                } else {
                    icon = new ImageIcon(img);
                }
            }

            JTextField ipField = new JTextField("localhost", 20);
            JTextField nicknameField = new JTextField(nickname, 16);
            Object[] message;
            if (icon != null) {
                final ImageIcon finalIcon = icon;
                JPanel imagePanel = new JPanel(new java.awt.GridBagLayout()) {
                    @Override
                    protected void paintComponent(java.awt.Graphics g) {
                        super.paintComponent(g);
                        java.awt.Image base = finalIcon.getImage();
                        int iw = finalIcon.getIconWidth();
                        int ih = finalIcon.getIconHeight();
                        int x = (getWidth() - iw) / 2;
                        int y = (getHeight() - ih) / 2;
                        g.drawImage(base, x, y, iw, ih, this);
                    }

                    @Override
                    public java.awt.Dimension getPreferredSize() {
                        return new java.awt.Dimension(finalIcon.getIconWidth(), finalIcon.getIconHeight());
                    }
                };

                JPanel overlay = new JPanel();
                overlay.setOpaque(true);
                overlay.setBackground(new Color(0, 0, 0, 120));
                overlay.setBorder(new javax.swing.border.EmptyBorder(14, 16, 14, 16));
                overlay.setLayout(new BoxLayout(overlay, BoxLayout.Y_AXIS));

                JPanel ipRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 6));
                ipRow.setOpaque(false);
                JLabel prompt = new JLabel("서버 IP 주소:");
                prompt.setForeground(Color.WHITE);
                ipRow.add(prompt);
                ipRow.add(ipField);

                JPanel nameRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 6));
                nameRow.setOpaque(false);
                JLabel nameLabel = new JLabel("닉네임");
                nameLabel.setForeground(Color.WHITE);
                nameRow.add(nameLabel);
                nameRow.add(nicknameField);

                overlay.add(ipRow);
                overlay.add(Box.createVerticalStrut(6));
                overlay.add(nameRow);

                java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.anchor = java.awt.GridBagConstraints.CENTER;
                imagePanel.add(overlay, gbc);

                message = new Object[]{imagePanel};
            } else {
                JPanel simplePanel = new JPanel(new java.awt.GridLayout(2, 2, 6, 6));
                simplePanel.add(new JLabel("서버 IP 주소:"));
                simplePanel.add(ipField);
                simplePanel.add(new JLabel("닉네임"));
                simplePanel.add(nicknameField);
                message = new Object[]{simplePanel};
            }

            int result = JOptionPane.showConfirmDialog(null, message, "서버 접속", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                ip = ipField.getText().trim();
                String enteredNickname = nicknameField.getText().trim();
                if (!enteredNickname.isEmpty()) {
                    nickname = enteredNickname;
                }
            }
        } catch (Exception e) {
            ip = JOptionPane.showInputDialog(null, "서버 IP 주소를 입력하세요", "localhost");
        }
        if (ip == null || ip.isBlank()) return;

        try {
            socket = new Socket(ip, PORT);
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            System.out.println("클라이언트가 서버에 연결 성공.");

            // 닉네임 핸드셰이크
            sendMessage(new NetworkProtocol.Msg_C2S_Handshake(nickname));

            SwingUtilities.invokeLater(() -> {
                lobbyFrame = new LobbyFrame(this, nickname);
                lobbyFrame.setVisible(true);
            });

            new Thread(this::listenToServer).start();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "서버 접속 실패: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 서버로부터 오는 메시지를 계속 수신 */
    private void listenToServer() {
        try {
            while (socket != null && !socket.isClosed()) {
                Object msg = ois.readObject();

                // --- 로비/대기 메시지 ---
                if (msg instanceof NetworkProtocol.Msg_S2C_RoomList m) {
                    SwingUtilities.invokeLater(() -> lobbyFrame.setRooms(m.rooms()));
                } else if (msg instanceof NetworkProtocol.Msg_S2C_RoomUpdated m) {
                    SwingUtilities.invokeLater(() -> {
                        lobbyFrame.upsertRoom(m.room());
                        if (waitingRoomFrame != null && m.room().name().equals(waitingRoomFrame.getRoomName())) {
                            waitingRoomFrame.updateRoomInfo(m.room());
                        }
                    });
                } else if (msg instanceof NetworkProtocol.Msg_S2C_RoomRemoved m) {
                    SwingUtilities.invokeLater(() -> lobbyFrame.removeRoom(m.roomName()));
                } else if (msg instanceof NetworkProtocol.Msg_S2C_EnterWaitingRoom m) {
                    SwingUtilities.invokeLater(() -> openWaitingRoom(m.room(), m.players(), m.myTeam()));
                } else if (msg instanceof NetworkProtocol.Msg_S2C_PlayerListUpdated m) {
                    SwingUtilities.invokeLater(() -> {
                        if (waitingRoomFrame != null) waitingRoomFrame.updatePlayers(m.players());
                    });
                } else if (msg instanceof NetworkProtocol.Msg_S2C_WaitingChat m) {
                    SwingUtilities.invokeLater(() -> {
                        if (waitingRoomFrame != null) waitingRoomFrame.appendChat(m.sender(), m.text());
                    });
                } else if (msg instanceof NetworkProtocol.Msg_S2C_RoomResponseFailure m) {
                    SwingUtilities.invokeLater(() ->
                        lobbyFrame.setStatus(m.reason(), Color.RED));
                    JOptionPane.showMessageDialog(lobbyFrame, m.reason(), "오류", JOptionPane.ERROR_MESSAGE);
                } else if (msg instanceof NetworkProtocol.Msg_S2C_ReturnToLobby) {
                    handleReturnToLobby("게임 종료. 로비로 복귀합니다");
                } else if (msg instanceof NetworkProtocol.Msg_S2C_OpponentLeft) {
                    JOptionPane.showMessageDialog(gameFrame, "상대방이 나갔습니다. 로비로 복귀합니다", "게임 중단", JOptionPane.WARNING_MESSAGE);
                    handleReturnToLobby("상대방이 나갔습니다");

                // --- 게임 시작/진행 메시지 ---
                } else if (msg instanceof NetworkProtocol.Msg_S2C_GameStart m) {
                    initializeGame(m.assignedTeam(), m.board(), m.secondsLeft());
                } else if (gameFrame != null) {
                    if (msg instanceof NetworkProtocol.Msg_S2C_BroadcastInput m) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteInput(m.team(), m.input()));
                    } else if (msg instanceof NetworkProtocol.Msg_S2C_Tick) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteTick());
                    } else if (msg instanceof NetworkProtocol.Msg_S2C_GameOver) {
                        SwingUtilities.invokeLater(() -> gameFrame.handleRemoteGameOver());
                        Thread.sleep(3000); // 결과 표시 시간
                        handleGameFinished();
                    }
                }
            }
        } catch (EOFException | SocketException e) {
            System.out.println("클라이언트가 서버와 연결을 잃었습니다");
            if (gameFrame != null) gameFrame.dispose();
            if (lobbyFrame != null) lobbyFrame.dispose();
            JOptionPane.showMessageDialog(null, "서버와 연결이 끊어졌습니다.", "연결 오류", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }
    
    /** (S2C) 게임 시작 메시지 수신 시 호출 */
    private void initializeGame(Team myTeam, Board board, int seconds) {
        TokenIndex localIndex = new TokenIndex();
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                Cell cell = board.get(r, c);
                localIndex.add(cell.owner(), cell.token(), new Pos(r, c));
            }
        }
        localModel = new GameModel(board, localIndex, seconds, 1, WordPool.fromBoard(board));

        SwingUtilities.invokeLater(() -> {
            if (lobbyFrame != null) {
                lobbyFrame.setVisible(false);
            }
            if (waitingRoomFrame != null) {
                waitingRoomFrame.setVisible(false);
            }
            gameFrame = new GameFrame(localModel, this, myTeam);
            gameFrame.setVisible(true);
        });
    }

    /** (S2C) 게임 종료 -> 대기방 복귀 */
    private void handleGameFinished(){
        SwingUtilities.invokeLater(()-> {
            if(gameFrame != null){
                gameFrame.dispose();
                gameFrame = null;
                localModel = null;
            }
            if(waitingRoomFrame != null){
                waitingRoomFrame.setVisible(true);
                waitingRoomFrame.appendChat("SYSTEM", "게임이 종료되었습니다. 다시 시작하려면 준비하세요.");
            }else{
                //waitingRoomFrame이 없는 비정상적인 경우 로비로 이동
                handleReturnToLobby("게임 종료. 로비로 복귀합니다.");
            }
        });
    }
    
    /** (S2C) 게임 종료/중단 -> 로비 복귀 */
    private void handleReturnToLobby(String message) {
        SwingUtilities.invokeLater(() -> {
            if (gameFrame != null) {
                gameFrame.dispose();
                gameFrame = null;
                localModel = null;
            }
            if (waitingRoomFrame != null) {
                waitingRoomFrame.dispose();
                waitingRoomFrame = null;
            }
            if (lobbyFrame != null) {
                lobbyFrame.setVisible(true);
                lobbyFrame.setStatus(message, Color.WHITE);
            }
        });
    }

    /** (C2S) 메시지 전송 (범용) */
    private void sendMessage(Serializable message) {
        try {
            if (oos != null) {
                oos.writeObject(message);
                oos.flush();
            }
        } catch (IOException e) {
            System.err.println("클라이언트 메시지 전송 오류 - " + e.getMessage());
        }
    }
    
    // --- UI가 호출하는 메소드 ---

    /** (C2S) (CreateRoomDialog) 방 생성 요청 */
    public void sendCreateRoomRequest(String roomName, String password, int seconds, Team team) {
        sendMessage(new NetworkProtocol.Msg_C2S_CreateRoom(roomName, password, seconds, team));
        if (lobbyFrame != null) lobbyFrame.setStatus("방 생성 요청 중...", Color.GRAY);
    }
    
    /** (C2S) (JoinRoomDialog) 방 참여 요청 */
    public void sendJoinRoomRequest(String roomName, String password) {
        sendMessage(new NetworkProtocol.Msg_C2S_JoinRoom(roomName, password));
        if (lobbyFrame != null) lobbyFrame.setStatus("방 참여 요청 중...", Color.GRAY);
    }

    /** (C2S) 로비 방 목록 요청 */
    public void requestRoomList() {
        sendMessage(new NetworkProtocol.Msg_C2S_RequestRoomList());
    }

    /** (C2S) 대기방 게임 시작 */
    public void requestStartGame() {
        sendMessage(new NetworkProtocol.Msg_C2S_StartGame());
    }

    /** (C2S) 대기방 준비 토글 */
    public void sendToggleReady(boolean ready) {
        sendMessage(new NetworkProtocol.Msg_C2S_ToggleReady(ready));
    }

    /** (C2S) 대기방 채팅 */
    public void sendWaitingChat(String text) {
        sendMessage(new NetworkProtocol.Msg_C2S_WaitingChat(text));
    }

    /** (C2S) 방 나가기 (대기방) */
    public void leaveRoom() {
        sendMessage(new NetworkProtocol.Msg_C2S_LeaveRoom());
        handleReturnToLobby("방에서 나갔습니다");
    }

    /** (C2S) (GameFrame) 게임에서 입력 요청 */
    public void sendInputRequest(Team team, String input) {
        sendMessage(new NetworkProtocol.Msg_C2S_InputRequest(team, input));
    }
    
    /** (C2S) (GameFrame) 게임방 나가기(X버튼) */
    public void disconnectFromGame() {
        sendMessage(new NetworkProtocol.Msg_C2S_LeaveRoom());
        handleReturnToLobby("게임방을 나갔습니다");
    }

    public String getNickname() {
        return nickname;
    }

    /** 대기방 UI 열기 */
    private void openWaitingRoom(NetworkProtocol.RoomInfo roomInfo, java.util.List<NetworkProtocol.PlayerInfo> players, Team myTeam) {
        if (lobbyFrame != null) {
            lobbyFrame.setVisible(false);
        }
        if (waitingRoomFrame != null) {
            waitingRoomFrame.dispose();
        }
        waitingRoomFrame = new WaitingRoomFrame(this, roomInfo, players, myTeam);
        waitingRoomFrame.setVisible(true);
    }

    /** (종료) */
    public void stop() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { /* 무시 */ }
        System.out.println("클라이언트가 연결 종료합니다.");
        System.exit(0);
    }

    /**
     * 주어진 경로에서 이미지를 로드하려 시도합니다
     * - 파일 시스템 직접 경로
     * - user.dir 기준 상대
     * - 클래스패스 리소스
     */
    private BufferedImage tryLoadImage(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                return ImageIO.read(f);
            }

            File f2 = new File(System.getProperty("user.dir"), path);
            if (f2.exists()) {
                return ImageIO.read(f2);
            }

            java.net.URL res = GameClient.class.getResource("/" + path);
            if (res != null) {
                return ImageIO.read(res);
            }
        } catch (Exception e) {
            System.out.println("이미지 로드 실패: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args) {
        GameClient client = new GameClient();
        client.start();
    }
}
