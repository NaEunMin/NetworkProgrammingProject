package ui;
import javax.swing.*;

import client.GameClient;

import java.awt.*;

/**
 * '방 참여' 팝업 다이얼로그 (Modal)
 * 
 * [설계]
 * - JDialog(modal=true)로 구현하여, 이 창이 열려있는 동안 부모 창(LobbyFrame) 조작 방지
 * - GridBagLayout을 사용하여 라벨과 입력 필드를 정렬 (GridBagLayout은 복잡하지만 정교한 배치 가능)
 * - "참여" 버튼 클릭 시 GameClient를 통해 서버로 JoinRoom 요청 전송
 */
public class JoinRoomDialog extends JDialog {

    private final GameClient client;
    private JTextField roomNameField;
    private JPasswordField passwordField;

    public JoinRoomDialog(JFrame parent, GameClient client) {
        super(parent, "방 참여", true); // modal = true
        this.client = client;
        
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 컴포넌트 간 여백
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. 방 이름 입력 필드
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("방 이름:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 2;
        roomNameField = new JTextField(15);
        add(roomNameField, gbc);

        // 2. 비밀번호 입력 필드 (JPasswordField 사용으로 입력 내용 숨김)
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("비밀번호:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        passwordField = new JPasswordField(10);
        add(passwordField, gbc);
        
        // 3. 버튼 패널 (FlowLayout으로 우측 정렬)
        JButton joinButton = new JButton("참여");
        JButton cancelButton = new JButton("취소");
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(joinButton);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        add(buttonPanel, gbc);
        
        // --- 이벤트 리스너 ---
        
        cancelButton.addActionListener(e -> {
            dispose(); // 다이얼로그 닫기
        });

        joinButton.addActionListener(e -> {
            joinRoom();
        });

        pack(); // 컴포넌트 크기에 맞춰 창 크기 자동 조절
        setLocationRelativeTo(parent); // 부모 창 중앙에 표시
    }
    
    /**
     * 방 참여 요청 처리
     * 1. 입력값 검증 (방 이름 필수)
     * 2. 서버로 JoinRoom 메시지 전송
     * 3. 다이얼로그 닫기 (결과는 서버 응답으로 처리됨)
     */
    private void joinRoom() {
        String roomName = roomNameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (roomName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "방 이름을 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // GameClient를 통해 서버에 "방 참여 요청" 전송
        client.sendJoinRoomRequest(roomName, password);
        
        dispose();
    }
}