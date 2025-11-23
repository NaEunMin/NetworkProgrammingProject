import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 메인 로비 화면. 프로필과 룸 목록을 보여준다.
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
        
        // 이미지 로드
        try {
            backgroundImage = new ImageIcon("resources/images/lobby_background_pirate.png").getImage();
        } catch (Exception e) {
            e.printStackTrace();
        }

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
        // content.setBackground(new Color(14, 32, 52)); // 이미지 사용하므로 배경색 제거
        setContentPane(content);

        content.add(buildProfilePanel(), BorderLayout.WEST);
        content.add(buildLobbyPanel(), BorderLayout.CENTER);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                client.stop();
            }
        });
    }

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setOpaque(true);
        panel.setBackground(new Color(26, 52, 78, 200)); // 반투명
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

    private JPanel buildLobbyPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(true);
        panel.setBackground(new Color(19, 44, 68, 200)); // 반투명
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

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

        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setOpaque(false);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonPanel.setOpaque(false);
        JButton createRoomBtn = new JButton("방 만들기");
        JButton joinRoomBtn = new JButton("방 참여");
        JButton singlePlayBtn = new JButton("싱글 플레이"); // [NEW]

        buttonPanel.add(singlePlayBtn);
        buttonPanel.add(createRoomBtn);
        buttonPanel.add(joinRoomBtn);

        singlePlayBtn.addActionListener(e -> {
            // 난이도 선택 다이얼로그
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

    public void setRooms(java.util.List<NetworkProtocol.RoomInfo> rooms) {
        roomListModel.clear();
        for (NetworkProtocol.RoomInfo r : rooms) {
            roomListModel.addElement(r);
        }
        if (!rooms.isEmpty()) {
            roomList.setSelectedIndex(0);
        }
    }

    public void upsertRoom(NetworkProtocol.RoomInfo room) {
        int idx = findIndex(room.name());
        if (idx >= 0) {
            roomListModel.setElementAt(room, idx);
        } else {
            roomListModel.addElement(room);
        }
        roomList.setSelectedIndex(roomListModel.size() - 1);
        roomList.ensureIndexIsVisible(roomListModel.size() - 1);
    }

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
