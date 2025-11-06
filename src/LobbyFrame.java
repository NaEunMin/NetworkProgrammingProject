import javax.swing.*;
import java.awt.*;

/**
 * 클라이언트가 서버 접속 후 가장 먼저 보는 메인 화면 (로비).
 * 방 생성 또는 방 참여를 선택할 수 있다.
 */
public class LobbyFrame extends JFrame {

    private final GameClient client;
    private JLabel statusLabel;

    public LobbyFrame(GameClient client) {
        super("판 뒤집기 - 로비");
        this.client = client;

        setTitle("판 뒤집기 - 로비");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // 배경색 설정
        getContentPane().setBackground(new Color(19, 36, 49));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // "방 만들기" 버튼
        JButton createRoomBtn = new JButton("방 만들기");
        createRoomBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridy = 0;
        panel.add(createRoomBtn, gbc);

        // "방 참여" 버튼
        JButton joinRoomBtn = new JButton("방 참여");
        joinRoomBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridy = 1;
        panel.add(joinRoomBtn, gbc);
        
        // 상태 메시지 라벨
        statusLabel = new JLabel("서버에 접속되었습니다.", SwingConstants.CENTER);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        gbc.gridy = 2;
        panel.add(statusLabel, gbc);

        add(panel, BorderLayout.CENTER);

        // --- 이벤트 리스너 ---
        
        createRoomBtn.addActionListener(e -> {
            // 방 만들기 다이얼로그 띄우기
            CreateRoomDialog dialog = new CreateRoomDialog(this, client);
            dialog.setVisible(true);
        });

        joinRoomBtn.addActionListener(e -> {
            // 방 참여 다이얼로그 띄우기
            JoinRoomDialog dialog = new JoinRoomDialog(this, client);
            dialog.setVisible(true);
        });
        
        // X 버튼 클릭 시
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                client.stop(); // 클라이언트 완전 종료
            }
        });
    }
    
    /** 서버로부터 메시지를 받아 상태 라벨 업데이트 (EDT에서 호출 보장) */
    public void setStatus(String message, Color color) {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
    }
}