import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * 대기방 화면: 팀 슬롯, 방 정보, 채팅 UI
 */
public class WaitingRoomFrame extends JFrame {

    private final GameClient client;
    private NetworkProtocol.RoomInfo roomInfo;
    private final Team myTeam;
    private boolean iAmOwner = false;
    private boolean amReady = false;

    private JLabel roomNameLabel;
    private JLabel timeLabel;
    private JLabel yellowSlot;
    private JLabel blueSlot;
    private JButton startButton;
    private JButton readyButton;
    private JTextArea chatArea;
    private JTextField chatInput;

    private Image backgroundImage;

    public WaitingRoomFrame(GameClient client, NetworkProtocol.RoomInfo roomInfo, List<NetworkProtocol.PlayerInfo> players, Team myTeam) {
        super("대기방 - " + roomInfo.name());
        this.client = client;
        this.roomInfo = roomInfo;
        this.myTeam = myTeam;

        setSize(920, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // 이미지 로드
        try {
            backgroundImage = new ImageIcon("resources/images/waiting_room_background_pirate.png").getImage();
        } catch (Exception e) {
            e.printStackTrace();
        }

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                client.leaveRoom();
            }
        });

        setContentPane(buildContent());
        updatePlayers(players);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12)) {
             @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        // root.setBackground(new Color(19, 44, 68)); // 이미지 사용

        root.add(buildInfoPanel(), BorderLayout.WEST);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildChatPanel(), BorderLayout.EAST);

        return root;
    }

    private JPanel buildInfoPanel() {
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setPreferredSize(new Dimension(220, 0));
        info.setBackground(new Color(26, 52, 78, 200)); // 반투명
        info.setBorder(new EmptyBorder(16, 16, 16, 16));

        roomNameLabel = new JLabel(roomInfo.name());
        roomNameLabel.setForeground(Color.WHITE);
        roomNameLabel.setFont(roomNameLabel.getFont().deriveFont(Font.BOLD, 18f));

        timeLabel = new JLabel("게임 시간: " + roomInfo.seconds() + "초");
        timeLabel.setForeground(new Color(200, 215, 230));
        timeLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        info.add(roomNameLabel);
        info.add(timeLabel);
        info.add(Box.createVerticalGlue());

        return info;
    }

    private JPanel buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);

        JPanel teams = new JPanel(new GridLayout(1, 2, 12, 0));
        teams.setOpaque(false);

        yellowSlot = createSlotLabel("노랑팀");
        blueSlot = createSlotLabel("파랑팀");

        JPanel yellowPanel = wrapSlot("노랑팀", yellowSlot, new Color(250, 215, 120));
        JPanel bluePanel = wrapSlot("파랑팀", blueSlot, new Color(140, 190, 255));

        teams.add(yellowPanel);
        teams.add(bluePanel);

        JPanel bottomButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        bottomButtons.setOpaque(false);
        JButton leaveButton = new JButton("방 나가기");
        readyButton = new JButton("준비!");
        startButton = new JButton("게임 시작");
        startButton.setEnabled(false);
        bottomButtons.add(leaveButton);
        bottomButtons.add(readyButton);
        bottomButtons.add(startButton);

        leaveButton.addActionListener(e -> client.leaveRoom());
        startButton.addActionListener(e -> client.requestStartGame());
        readyButton.addActionListener(e -> toggleReady());

        center.add(teams, BorderLayout.CENTER);
        center.add(bottomButtons, BorderLayout.SOUTH);
        return center;
    }

    private JPanel wrapSlot(String title, JLabel slot, Color headerColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(30, 58, 88, 200)); // 반투명
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel(title);
        header.setOpaque(true);
        header.setBackground(headerColor);
        header.setForeground(Color.BLACK);
        header.setHorizontalAlignment(SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setBorder(new EmptyBorder(6, 0, 6, 0));

        panel.add(header, BorderLayout.NORTH);
        panel.add(slot, BorderLayout.CENTER);
        return panel;
    }

    private JLabel createSlotLabel(String placeholder) {
        JLabel label = new JLabel(placeholder, SwingConstants.CENTER);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        label.setOpaque(true);
        label.setBackground(new Color(38, 78, 120, 200)); // 반투명
        label.setBorder(new EmptyBorder(32, 8, 32, 8));
        return label;
    }

    private JPanel buildChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout(8, 8));
        chatPanel.setPreferredSize(new Dimension(260, 0));
        chatPanel.setBackground(new Color(26, 52, 78, 200)); // 반투명
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel chatTitle = new JLabel("채팅");
        chatTitle.setForeground(Color.WHITE);
        chatTitle.setFont(chatTitle.getFont().deriveFont(Font.BOLD, 16f));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(19, 44, 68));
        chatArea.setForeground(Color.WHITE);

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        chatInput = new JTextField();
        JButton sendBtn = new JButton("전송");

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        chatInput.addActionListener(e -> submitChat());
        sendBtn.addActionListener(e -> submitChat());

        chatPanel.add(chatTitle, BorderLayout.NORTH);
        chatPanel.add(scroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);
        return chatPanel;
    }

    private void submitChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        client.sendWaitingChat(text);
        chatInput.setText("");
    }

    /** 서버에서 받은 플레이어 목록 업데이트 */
    public void updatePlayers(List<NetworkProtocol.PlayerInfo> players) {
        String yellowName = "빈 자리";
        String blueName = "빈 자리";
        iAmOwner = false;
        amReady = false;
        for (NetworkProtocol.PlayerInfo p : players) {
            String name = p.nickname();
            if (p.team() == myTeam) {
                name = name + " (나)";
                if (p.owner()) iAmOwner = true;
                amReady = p.ready();
            }
            String readyTag = p.ready() ? " - 준비" : "";
            if (p.team() == Team.YELLOW) {
                yellowName = name + readyTag;
            } else if (p.team() == Team.BLUE) {
                blueName = name + readyTag;
            }
        }
        yellowSlot.setText(yellowName);
        blueSlot.setText(blueName);

        readyButton.setText(amReady ? "준비 해제" : "준비!");

        boolean allReady = players.size() >= 2 && players.stream().allMatch(NetworkProtocol.PlayerInfo::ready);
        startButton.setVisible(iAmOwner);
        startButton.setEnabled(iAmOwner && allReady);
    }

    /** 채팅 로그 추가 */
    public void appendChat(String sender, String text) {
        chatArea.append(sender + ": " + text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void updateRoomInfo(NetworkProtocol.RoomInfo roomInfo) {
        this.roomInfo = roomInfo;
        roomNameLabel.setText(roomInfo.name());
        timeLabel.setText("게임 시간: " + roomInfo.seconds() + "초");
    }

    private void toggleReady() {
        client.sendToggleReady(!amReady);
    }

    public String getRoomName() {
        return roomInfo.name();
    }
}
