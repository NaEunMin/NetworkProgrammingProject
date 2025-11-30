package ui;
import javax.swing.*;

import client.GameClient;
import game.Team;
import protocol.NetworkProtocol;

import java.awt.*;

/**
 * '방 만들기' 팝업 다이얼로그 (Modal)
 * 
 * [설계]
 * - 방 생성에 필요한 다양한 옵션(이름, 비번, 시간, 보너스, 테마, 팀)을 설정
 * - GridBagLayout을 사용하여 복잡한 폼 레이아웃 구성
 * - 테마 선택 시 팀 이름이 동적으로 변경되는 UI 로직 포함 (예: 해적 테마 -> 노랑/파랑, 야시장 -> 보라/주황)
 */
public class CreateRoomDialog extends JDialog {

    private final GameClient client;

    private JTextField roomNameField;
    private JCheckBox privateCheckBox;
    private JPasswordField passwordField;
    private JComboBox<String> timeComboBox;
    private JRadioButton yellowTeamButton, blueTeamButton;
    private JCheckBox bonusCheckBox;
    private JComboBox<NetworkProtocol.Theme> themeComboBox;

    public CreateRoomDialog(JFrame parent, GameClient client) {
        super(parent, "방 만들기", true); // Modal = true (부모 창 조작 방지)
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

        // 2. 비밀번호 (체크박스로 활성화/비활성화 제어)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        privateCheckBox = new JCheckBox("비공개 설정");
        add(privateCheckBox, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.gridwidth = 2;
        passwordField = new JPasswordField(10);
        passwordField.setEnabled(false); // 기본 비활성화
        add(passwordField, gbc);

        // 3. 게임 시간 선택
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        add(new JLabel("게임 시간:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.gridwidth = 2;
        String[] times = { "1분", "2분", "3분" };
        timeComboBox = new JComboBox<>(times);
        add(timeComboBox, gbc);

        // 3.5 보너스 게임 설정
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        add(new JLabel("보너스 게임:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.gridwidth = 2;
        bonusCheckBox = new JCheckBox("ON");
        bonusCheckBox.setSelected(true);
        bonusCheckBox.addActionListener(e -> bonusCheckBox.setText(bonusCheckBox.isSelected() ? "ON" : "OFF"));
        add(bonusCheckBox, gbc);

        // 3.6 테마 선택 (Renderer를 커스텀하여 Enum의 label 표시)
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 1;
        add(new JLabel("테마 선택:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2;
        themeComboBox = new JComboBox<>(NetworkProtocol.Theme.values());
        themeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof NetworkProtocol.Theme t) {
                    setText(t.getLabel());
                }
                return this;
            }
        });
        add(themeComboBox, gbc);

        // 4. 팀 선택 (라디오 버튼 그룹)
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1;
        add(new JLabel("팀 선택:"), gbc);

        yellowTeamButton = new JRadioButton("노랑팀");
        yellowTeamButton.setSelected(true);
        blueTeamButton = new JRadioButton("파랑팀");

        ButtonGroup teamGroup = new ButtonGroup();
        teamGroup.add(yellowTeamButton);
        teamGroup.add(blueTeamButton);

        JPanel teamPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        teamPanel.add(yellowTeamButton);
        teamPanel.add(blueTeamButton);

        gbc.gridx = 1; gbc.gridy = 5; gbc.gridwidth = 2;
        add(teamPanel, gbc);

        // 5. 버튼 패널
        JButton createButton = new JButton("방 생성");
        JButton cancelButton = new JButton("취소");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        add(buttonPanel, gbc);

        // --- 이벤트 리스너 ---

        // 비밀번호 체크박스 토글 시 필드 활성화/비활성화
        privateCheckBox.addActionListener(e -> {
            passwordField.setEnabled(privateCheckBox.isSelected());
        });

        // 테마 변경 시 팀 이름 변경 (UI 피드백)
        themeComboBox.addActionListener(e -> {
            NetworkProtocol.Theme theme = (NetworkProtocol.Theme) themeComboBox.getSelectedItem();
            if (theme == NetworkProtocol.Theme.NIGHT_MARKET) {
                yellowTeamButton.setText("보라팀");
                blueTeamButton.setText("주황팀");
            } else {
                yellowTeamButton.setText("노랑팀");
                blueTeamButton.setText("파랑팀");
            }
        });

        cancelButton.addActionListener(e -> {
            dispose();
        });

        createButton.addActionListener(e -> {
            createRoom();
        });

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * 방 생성 요청 처리
     * 1. 입력값 검증 (이름 길이, 비밀번호 필수 여부)
     * 2. UI 선택값을 프로토콜 데이터로 변환
     * 3. 서버로 CreateRoom 메시지 전송
     */
    private void createRoom() {
        String roomName = roomNameField.getText().trim();
        if (roomName.isEmpty() || roomName.length() > 20) {
            JOptionPane.showMessageDialog(this, "방 이름은 1~20자 사이여야 합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String password = "";
        if (privateCheckBox.isSelected()) {
            password = new String(passwordField.getPassword());
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "비밀번호를 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        int seconds = switch (timeComboBox.getSelectedIndex()) {
            case 0 -> 60;
            case 1 -> 120;
            case 2 -> 180;
            default -> 60;
        };

        boolean bonusEnabled = bonusCheckBox.isSelected();
        NetworkProtocol.Theme theme = (NetworkProtocol.Theme) themeComboBox.getSelectedItem();
        Team chosenTeam = yellowTeamButton.isSelected() ? Team.YELLOW : Team.BLUE;

        // GameClient를 통해 서버에 "방 생성 요청" 전송
        client.sendCreateRoomRequest(roomName, password, seconds, bonusEnabled, theme, chosenTeam);

        dispose(); // 요청 후 다이얼로그 닫기
    }
}