import javax.swing.*;
import java.awt.*;

/**
 * '방 참여' 팝업 다이얼로그
 */
public class JoinRoomDialog extends JDialog {

    private final GameClient client;
    private JTextField roomNameField;
    private JPasswordField passwordField;

    public JoinRoomDialog(JFrame parent, GameClient client) {
        super(parent, "방 참여", true);
        this.client = client;
        
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. 방 이름
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("방 이름:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        roomNameField = new JTextField(15);
        add(roomNameField, gbc);

        // 2. 비밀번호
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("비밀번호:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        passwordField = new JPasswordField(10);
        add(passwordField, gbc);
        
        // 3. 버튼
        JButton joinButton = new JButton("참여");
        JButton cancelButton = new JButton("취소");
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(joinButton);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        add(buttonPanel, gbc);
        
        // --- 리스너 ---
        
        cancelButton.addActionListener(e -> {
            dispose();
        });

        joinButton.addActionListener(e -> {
            joinRoom();
        });

        pack();
        setLocationRelativeTo(parent);
    }
    
    private void joinRoom() {
        String roomName = roomNameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "방 이름을 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // GameClient를 통해 서버에 "방 참여 요청"
        client.sendJoinRoomRequest(roomName, password);
        
        dispose();
    }
}