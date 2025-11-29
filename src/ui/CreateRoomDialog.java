package ui;
import javax.swing.*;

import client.GameClient;
import game.Team;
import protocol.NetworkProtocol;

import java.awt.*;

/**
 * import javax.swing.*;
 * import java.awt.*;
 * 
 * /**
 * '방 만들기' 팝업 다이얼로그
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
        super(parent, "방 만들기", true); // Modal
        this.client = client;

        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. 방 이름
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(new JLabel("방 이름:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        roomNameField = new JTextField(15);
        add(roomNameField, gbc);

        // 2. 비밀번호
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        privateCheckBox = new JCheckBox("비공개 설정");
        add(privateCheckBox, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        passwordField = new JPasswordField(10);
        passwordField.setEnabled(false); // 기본 비활성화
        add(passwordField, gbc);

        // 3. 게임 시간
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        add(new JLabel("게임 시간:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        String[] times = { "1분", "2분", "3분" };
        timeComboBox = new JComboBox<>(times);
        add(timeComboBox, gbc);

        // [NEW] 3.5 보너스 게임 설정
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        add(new JLabel("보너스 게임:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        bonusCheckBox = new JCheckBox("ON");
        bonusCheckBox.setSelected(true);
        bonusCheckBox.addActionListener(e -> bonusCheckBox.setText(bonusCheckBox.isSelected() ? "ON" : "OFF"));
        add(bonusCheckBox, gbc);

        // [NEW] 3.6 테마 선택
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        add(new JLabel("테마 선택:"), gbc);
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
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

        // 4. 팀 선택
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
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

        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        add(teamPanel, gbc);

        // 5. 버튼
        JButton createButton = new JButton("방 생성");
        JButton cancelButton = new JButton("취소");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(createButton);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        add(buttonPanel, gbc);

        // --- 리스너 ---

        privateCheckBox.addActionListener(e -> {
            passwordField.setEnabled(privateCheckBox.isSelected());
        });

        // 테마 변경 시 팀 이름 변경
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
            dispose(); // 창 닫기
        });

        createButton.addActionListener(e -> {
            createRoom();
        });

        pack();
        setLocationRelativeTo(parent);
    }

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

        // GameClient를 통해 서버에 "방 생성 요청"
        client.sendCreateRoomRequest(roomName, password, seconds, bonusEnabled, theme, chosenTeam);

        dispose(); // 요청 후 다이얼로그 닫기
    }
}