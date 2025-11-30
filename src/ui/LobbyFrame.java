package ui;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import client.GameClient;
import game.SingleGameManager;
import protocol.NetworkProtocol;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 게임 로비 화면 (메인 메뉴)
 * 
 * [설계]
 * - 로그인 성공 후 진입하는 메인 화면
 * - 좌측: 사용자 프로필 정보 표시
 * - 중앙: 개설된 방 목록 표시 (JList + Custom Renderer)
 * - 하단: 방 만들기, 참여, 싱글 플레이 버튼
 * 
 * [UI 특징]
 * - 배경 이미지를 그리기 위해 커스텀 JPanel(paintComponent 오버라이딩) 사용
 * - 반투명 패널(Alpha composite color)을 사용하여 배경이 비치도록 연출
 */
public class LobbyFrame extends JFrame {

    private final GameClient client;
    private final String nickname;
    private JLabel statusLabel;
    private DefaultListModel<NetworkProtocol.RoomInfo> roomListModel;
    private JList<NetworkProtocol.RoomInfo> roomList;

    private Image backgroundImage;

    public LobbyFrame(GameClient client, String nickname) {
        super("판 뒤집기 - 로비");
        this.client = client;
        this.nickname = nickname;

        setTitle("판 뒤집기 - 로비");
        setSize(900, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // 배경 이미지 로드 (실패 시 예외 처리)
        try {
            backgroundImage = new ImageIcon("resources/images/lobby_background_pirate.png").getImage();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 배경 이미지를 그리는 메인 패널
        JPanel content = new JPanel(new BorderLayout(12, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        // 좌측 프로필 패널, 중앙 로비 패널 배치
        content.add(buildProfilePanel(), BorderLayout.WEST);
        content.add(buildLobbyPanel(), BorderLayout.CENTER);

        // 창 닫을 때 클라이언트 연결 종료
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                client.stop();
            }
        });
    }

    /**
     * 좌측 프로필 패널 생성
     * - 아바타, 닉네임, 환영 메시지 표시
     */
    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setOpaque(false); // 배경 그리기 위임
        panel.setBackground(new Color(26, 52, 78, 200)); // 짙은 남색 반투명
        panel.setBorder(new EmptyBorder(20, 16, 20, 16));

        JLabel avatar = new JLabel(createAvatarIcon(96));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel(nickname, SwingConstants.CENTER);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 18f));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(12, 0, 6, 0));

        JLabel greeting = new JLabel("환영합니다!", SwingConstants.CENTER);
        greeting.setForeground(new Color(200, 215, 230));
        greeting.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel bubble = new JPanel(new GridLayout(2, 1, 0, 4));
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(18, 12, 12, 12));
        JLabel nickLabel = new JLabel("닉네임");
        nickLabel.setForeground(new Color(180, 195, 210));
        JLabel nickValue = new JLabel(nickname);
        nickValue.setForeground(Color.WHITE);
        nickValue.setFont(nickValue.getFont().deriveFont(Font.BOLD, 16f));
        bubble.add(nickLabel);
        bubble.add(nickValue);

        panel.add(avatar);
        panel.add(nameLabel);
        panel.add(greeting);
        panel.add(Box.createVerticalGlue());
        panel.add(bubble);

        return panel;
    }

    /**
     * 중앙 로비 패널 생성
     * - 방 목록 리스트, 버튼(생성/참여/싱글)
     */
    private JPanel buildLobbyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false);
        panel.setBackground(new Color(19, 44, 68, 200)); // 짙은 남색 반투명
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // 상단 헤더 (제목 + 새로고침 버튼)
        JLabel title = new JLabel("게임 로비");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JButton refreshButton = new JButton("새로고침");
        refreshButton.addActionListener(e -> {
            client.requestRoomList();
            setStatus("서버에 연결되어 있습니다.", new Color(0, 200, 255));
        });

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(refreshButton, BorderLayout.EAST);

        // 방 목록 리스트 (JList + Custom Renderer)
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setOpaque(false);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // 리스트 아이템 렌더링 커스터마이징
        roomList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JPanel cell = new JPanel(new BorderLayout());
            cell.setBorder(new EmptyBorder(10, 12, 10, 12));
            cell.setBackground(isSelected ? new Color(38, 78, 120) : new Color(30, 58, 88));

            JLabel roomTitle = new JLabel(value.name());
            roomTitle.setForeground(Color.WHITE);
            roomTitle.setFont(roomTitle.getFont().deriveFont(Font.BOLD, 15f));

            String status = value.playing() ? "진행중" : "대기중";
            String detail = status + " · " + value.currentPlayers() + "/" + value.maxPlayers() + " · " + value.seconds() + "초";
            JLabel subtitle = new JLabel(detail);
            subtitle.setForeground(new Color(180, 200, 220));

            cell.add(roomTitle, BorderLayout.NORTH);
            cell.add(subtitle, BorderLayout.SOUTH);
            return cell;
        });
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 하단 버튼 패널
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonPanel.setOpaque(false);
        JButton createRoomBtn = new JButton("방 만들기");
        JButton joinRoomBtn = new JButton("방 참여");
        JButton singlePlayBtn = new JButton("싱글 플레이");

        buttonPanel.add(singlePlayBtn);
        buttonPanel.add(createRoomBtn);
        buttonPanel.add(joinRoomBtn);

        // 싱글 플레이 버튼 핸들러
        singlePlayBtn.addActionListener(e -> {
            String[] options = {"쉬움", "보통", "어려움"};
            int choice = JOptionPane.showOptionDialog(this, "난이도를 선택하세요", "싱글 플레이",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            
            if (choice >= 0) {
                SingleGameManager.Difficulty diff = SingleGameManager.Difficulty.values()[choice];
                new SingleGameManager(this, nickname, diff).start();
            }
        });

        createRoomBtn.addActionListener(e -> {
            CreateRoomDialog dialog = new CreateRoomDialog(this, client);
            dialog.setVisible(true);
        });

        joinRoomBtn.addActionListener(e -> {
            JoinRoomDialog dialog = new JoinRoomDialog(this, client);
            dialog.setVisible(true);
        });

        statusLabel = new JLabel("서버에 연결되어 있습니다.");
        statusLabel.setForeground(new Color(200, 215, 230));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(buttonPanel, BorderLayout.CENTER);
        footer.add(statusLabel, BorderLayout.SOUTH);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * 방 목록 전체 갱신 (서버 응답 시 호출)
     */
    public void setRooms(java.util.List<NetworkProtocol.RoomInfo> rooms) {
        roomListModel.clear();
        for (NetworkProtocol.RoomInfo r : rooms) {
            roomListModel.addElement(r);
        }
        if (!rooms.isEmpty()) {
            roomList.setSelectedIndex(0);
        }
    }

    /**
     * 특정 방 정보 갱신 또는 추가 (브로드캐스트 수신 시 호출)
     */
    public void upsertRoom(NetworkProtocol.RoomInfo room) {
        int idx = findIndex(room.name());
        if (idx >= 0) {
            roomListModel.setElementAt(room, idx);
        } else {
            roomListModel.addElement(room);
        }
        // 목록이 갱신되면 마지막 항목 선택 및 스크롤
        roomList.setSelectedIndex(roomListModel.size() - 1);
        roomList.ensureIndexIsVisible(roomListModel.size() - 1);
    }

    /**
     * 방 삭제 (브로드캐스트 수신 시 호출)
     */
    public void removeRoom(String roomName) {
        int idx = findIndex(roomName);
        if (idx >= 0) {
            roomListModel.remove(idx);
        }
    }

    public void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }

    public String getSelectedRoomName() {
        NetworkProtocol.RoomInfo selected = roomList.getSelectedValue();
        return selected == null ? "" : selected.name();
    }

    private int findIndex(String roomName) {
        for (int i = 0; i < roomListModel.size(); i++) {
            if (roomListModel.get(i).name().equals(roomName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 간단한 아바타 아이콘 생성 (Graphics2D 드로잉)
     */
    private Icon createAvatarIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(40, 80, 115));
        g2.fillOval(0, 0, size - 1, size - 1);
        g2.setColor(new Color(120, 150, 180));
        g2.setStroke(new BasicStroke(3f));
        g2.drawOval(1, 1, size - 3, size - 3);

        g2.setColor(new Color(200, 215, 235));
        g2.fillOval(size / 3, size / 4, size / 3, size / 3);
        g2.fillRoundRect(size / 3, size / 2, size / 3, size / 3, size / 4, size / 4);

        g2.dispose();
        return new ImageIcon(img);
    }
}
