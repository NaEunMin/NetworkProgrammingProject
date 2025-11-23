import javax.swing.*;
import java.awt.*;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.sound.sampled.LineEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 메인 프레임: 사진의 화면 구성을 Swing으로 재현.
 *
 * 상단: [노랑팀 카드(점수)]  [중앙 타이머 00:53]  [파랑팀 카드(점수)]
 * 중앙: 보드(BoardPanel)
 * 하단: 좌측(노랑팀 입력창 + 버튼)  |  우측(파랑팀 입력창 + 버튼)
 *
 * [네트워크 변경점]
 * - GameModel을 직접 생성하지 않고, GameClient로부터 전달받음.
 * - GameClient를 통해 서버로 입력 이벤트를 전송.
 * - GameClient가 호출하는 public 메소드(handleRemote*)를 통해
 * 서버로부터 받은 상태(입력 결과, 시간)를 UI에 반영.
 * - 자신에게 할당된 팀(myTeam)의 입력창만 활성화.
 */
public class GameFrame extends JFrame {

    // ---- 모델/네트워크 ----
    private final GameModel model;
    private final GameClient client; // 서버와 통신할 클라이언트
    private final Team myTeam;       // 이 프레임의 플레이어 팀 (YELLOW or BLUE)

    // ---- UI 구성요소(상단) ----
    private final JLabel yellowScore = scoreBadge(new Color(0xF2, 0xC1, 0x4E));
    private final JLabel blueScore = scoreBadge(new Color(0x5D, 0xA3, 0xFA));
    private final JLabel timerLabel = new JLabel("01:00", SwingConstants.CENTER);
    private final String yellowPlayerName;
    private final String bluePlayerName;
    private final Image backgroundImage;

    // ---- 중앙 보드 ----
    private final BoardPanel boardPanel;
    private final CardLayout centerCardLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerCardLayout);

    // ---- 보너스 타임 UI ----
    private boolean isBonusTime = false;
    private final JPanel bonusTimePanel = new JPanel();
    private final JLabel bonusTitle = new JLabel("BONUS TIME!", SwingConstants.CENTER);
    private final List<JLabel> sentenceLabels = new ArrayList<>();

    // ---- 하단 입력(좌/우) ----
    private final JTextField yellowInput = new JTextField(18);
    private final JButton yellowBtn = new JButton("입력하기");
    private final JTextField blueInput = new JTextField(18);
    private final JButton blueBtn = new JButton("입력하기");
    private final JLabel yellowFlipLabel = flipCounterLabel();
    private final JLabel blueFlipLabel = flipCounterLabel();

    /**
     * 생성자:
     * 모델, 클라이언트, 내 팀을 외부(GameClient)에서 주입받음.
     */
    public GameFrame(GameModel model, GameClient client, Team myTeam, String yellowPlayerName, String bluePlayerName) {
        super("판 뒤집기 (1:1 · 실시간 · Swing) - " + myTeam + "팀");

        this.model = model;
        this.client = client;
        this.myTeam = myTeam;
        this.yellowPlayerName = yellowPlayerName;
        this.bluePlayerName = bluePlayerName;
        this.backgroundImage = loadImage("resources/images/game_background.png");
        this.boardPanel = new BoardPanel(model);
        
        // 타이머 초기화 (모델의 시간으로)
        timerLabel.setText(formatSec(model.secondsLeft()));

        // 보너스 타임 패널 설정
        bonusTimePanel.setLayout(new BoxLayout(bonusTimePanel, BoxLayout.Y_AXIS));
        bonusTimePanel.setBackground(new Color(19, 36, 49));
        bonusTimePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        bonusTitle.setFont(new Font("Serif", Font.BOLD, 48));
        bonusTitle.setForeground(Color.ORANGE);
        bonusTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        bonusTimePanel.add(bonusTitle);
        bonusTimePanel.add(Box.createVerticalStrut(20));
        for (int i = 0; i < 5; i++) {
            JLabel sentenceLabel = new JLabel("", SwingConstants.CENTER);
            sentenceLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 22));
            sentenceLabel.setForeground(Color.WHITE);
            sentenceLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            sentenceLabels.add(sentenceLabel);
            bonusTimePanel.add(sentenceLabel);
            bonusTimePanel.add(Box.createVerticalStrut(10));
        }

        // 중앙 패널에 카드 레이아웃으로 보드와 보너스 패널 추가
        centerPanel.add(boardPanel, "board");
        centerPanel.add(bonusTimePanel, "bonus");
        centerPanel.setOpaque(false);

        // 4) 상단 점수판 + 타이머 (사진 레이아웃을 흉내)
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        yellowScore.setText(model.getScore(Team.YELLOW) + "P");
        blueScore.setText(model.getScore(Team.BLUE) + "P");

        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 22f));
        timerLabel.setForeground(Color.WHITE);
        JPanel timerWrap = pill(timerLabel, new Color(26, 47, 60));

        top.add(pill(yellowScore, new Color(241, 209, 109)), BorderLayout.WEST);
        top.add(timerWrap, BorderLayout.CENTER);
        top.add(pill(blueScore, new Color(133, 171, 236)), BorderLayout.EAST);

        // 5) 하단 입력 영역(좌: 노랑 / 우: 파랑) — 동시에 입력 가능
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel inputPanel;
        if (myTeam == Team.YELLOW) {
            inputPanel = teamInputPanel("노랑팀", Team.YELLOW, yellowInput, yellowBtn, yellowFlipLabel, new Color(241, 209, 109));
        } else {
            inputPanel = teamInputPanel("파랑팀", Team.BLUE, blueInput, blueBtn, blueFlipLabel, new Color(133, 171, 236));
        }
        int desiredWidth = boardPanel.getPreferredSize().width + 40;
        inputPanel.setPreferredSize(new Dimension(desiredWidth, inputPanel.getPreferredSize().height));
        bottom.add(inputPanel);
        refreshFlipLabels();

        ImageIcon yellowTeamIcon = loadScaledIcon("resources/images/yellow_team.png", 140, 180);
        ImageIcon blueTeamIcon = loadScaledIcon("resources/images/blue_team.png", 140, 180);

        // 6) 프레임 레이아웃 조립
        JPanel middle = new JPanel(new BorderLayout(8, 0));
        middle.setOpaque(false);
        middle.add(buildSidePanel("노랑팀", yellowPlayerName, new Color(241, 209, 109), yellowTeamIcon, myTeam == Team.YELLOW), BorderLayout.WEST);
        middle.add(centerPanel, BorderLayout.CENTER);
        middle.add(buildSidePanel("파랑팀", bluePlayerName, new Color(133, 171, 236), blueTeamIcon, myTeam == Team.BLUE), BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
                } else {
                    g.setColor(new Color(19, 36, 49));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            }
        };
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        // 7) 이벤트 - "내 팀"의 입력만 서버로 전송
        yellowBtn.addActionListener(e -> handleLocalInput(Team.YELLOW, yellowInput));
        yellowInput.addActionListener(e -> handleLocalInput(Team.YELLOW, yellowInput));
        blueBtn.addActionListener(e -> handleLocalInput(Team.BLUE, blueInput));
        blueInput.addActionListener(e -> handleLocalInput(Team.BLUE, blueInput));

        // 8) "내 팀"이 아닌 입력창은 비활성화
        if (myTeam == Team.YELLOW) {
            blueInput.setEnabled(false);
            blueBtn.setEnabled(false);
        } else {
            yellowInput.setEnabled(false);
            yellowBtn.setEnabled(false);
        }

        // 9) 윈도우 설정
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // 클라이언트 종료 시 GameClient에서 처리
        pack();
        setLocationRelativeTo(null);
    }


    /** 사진의 '카드 배지' 느낌을 주기 위한 점수 라벨 스타일 */
    private static JLabel scoreBadge(Color fg) {
        JLabel l = new JLabel("0P", SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        l.setForeground(Color.BLACK); // 점수 텍스트 색상
        return l;
    }

    /** 라벨을 둥근 캡슐 형태의 패널로 감싸는 헬퍼(UI 데코) */
    private static JPanel pill(JComponent inner, Color bg) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            }
        };
        p.setOpaque(false); 
        inner.setOpaque(false);
        
        p.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    /** 좌/우 팀 입력 패널 구성 — 팀 색상/라벨/텍스트필드/버튼 */
    private JPanel teamInputPanel(String title, Team team, JTextField field, JButton btn, JLabel flipLabel, Color tone) {
        JLabel titleL = new JLabel(title);
        titleL.setFont(titleL.getFont().deriveFont(Font.BOLD, 14f));
        titleL.setForeground(Color.BLACK);

        btn.setFocusable(false);

        JPanel wrap = new JPanel(new BorderLayout(6, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tone);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel row = new JPanel(new BorderLayout(6, 6));
        row.setOpaque(false);
        row.add(field, BorderLayout.CENTER);
        row.add(btn, BorderLayout.EAST);

        JPanel flipPanel = new JPanel();
        flipPanel.setOpaque(false);
        flipPanel.setLayout(new BoxLayout(flipPanel, BoxLayout.Y_AXIS));
        JLabel flipTitle = new JLabel("내가 뒤집은 판");
        flipTitle.setForeground(Color.DARK_GRAY);
        flipTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        flipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel flipCard = new JPanel(new BorderLayout());
        flipCard.setOpaque(true);
        flipCard.setBackground(new Color(255, 255, 255, 210));
        flipCard.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        flipCard.add(flipLabel, BorderLayout.CENTER);

        flipPanel.add(flipTitle);
        flipPanel.add(Box.createVerticalStrut(6));
        flipPanel.add(flipCard);

        JPanel body = new JPanel(new BorderLayout(8, 6));
        body.setOpaque(false);
        body.add(flipPanel, BorderLayout.WEST);
        body.add(row, BorderLayout.CENTER);

        wrap.add(titleL, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.SOUTH);
        return wrap;
    }

    /**
     * (7) 로컬 입력 처리
     */
    private void handleLocalInput(Team team, JTextField field) {
        if (team != myTeam || model.secondsLeft() <= 0) return;

        String input = field.getText();
        if (input == null || input.isBlank()) return;

        if (isBonusTime) {
            client.sendSentenceInput(team, input);
        } else {
            client.sendInputRequest(team, input);
        }
    }

    /**
     * (8) 타이머가 0이 되면 입력 비활성화 (양쪽 모두)
     */
    private void disableInputs() {
        for (var c : new JComponent[]{yellowInput, yellowBtn, blueInput, blueBtn}) c.setEnabled(false);
    }

    /** (9) 남은 시간 "MM:SS" 포맷 */
    private static String formatSec(int sec) {
        int m = Math.max(0, sec) / 60;
        int s = Math.max(0, sec) % 60;
        return String.format("%02d:%02d", m, s);
    }
    
    /** (신규) 사운드 재생 헬퍼 */
    private void playSound(String soundFileName) {
        new Thread(() -> {
            try (AudioInputStream audioIn = AudioSystem.getAudioInputStream(new File("resources/sounds/" + soundFileName))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
            } catch (UnsupportedAudioFileException | java.io.IOException | LineUnavailableException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- [신규] 서버 메시지 처리기 (GameClient의 리스너 스레드가 호출) ---
    
    /**
     * (신규) 서버로부터 "시간 1초 경과" 메시지를 받았을 때 (EDT에서 호출 보장)
     */
    public void handleRemoteTick() {
        model.tickOneSecond(); 
        timerLabel.setText(formatSec(model.secondsLeft())); 
    }

    /**
     * (신규) 서버로부터 "입력 처리" 명령을 받았을 때 (EDT에서 호출 보장)
     */
    public void handleRemoteInput(Team team, String input) {
        if (model.secondsLeft() <= 0) return;

        java.util.List<GameModel.FlipResult> flips = model.flipByInput(team, input);
        
        if (!flips.isEmpty()) {
            playSound("bell.wav");
        }

        boardPanel.animateFlips(flips);
        yellowScore.setText(model.getScore(Team.YELLOW) + "P");
        blueScore.setText(model.getScore(Team.BLUE) + "P");
        refreshFlipLabels();

        if (team == myTeam) {
            JTextField myField = (myTeam == Team.YELLOW) ? yellowInput : blueInput;
            if (flips.isEmpty()) {
                myField.selectAll();
            } else {
                myField.setText("");
            }
        }
    }

    /**
     * (신규) 서버로부터 "게임 종료" 메시지를 받았을 때 (EDT에서 호출 보장)
     */
    public void handleRemoteGameOver() {
        playSound("finish.wav");
        disableInputs();
        
        int y = model.getScore(Team.YELLOW);
        int b = model.getScore(Team.BLUE);
        String msg = (y == b) ? "비겼습니다!"
                : (y > b ? "노랑팀 승리!" : "파랑팀 승리!");
        JOptionPane.showMessageDialog(this, msg + "  (노랑 " + y + " / 파랑 " + b + ")", "게임 종료", JOptionPane.INFORMATION_MESSAGE);
        client.gameHasFinished();
    }

    // --- [추가] 보너스 타임 관련 서버 메시지 처리기 ---

    /**
     * (신규) 서버로부터 "보너스 타임 시작" 메시지를 받았을 때
     */
    public void handleBonusTimeStart(java.util.List<String> sentences) {
        isBonusTime = true;
        
        for (int i = 0; i < sentenceLabels.size(); i++) {
            if (i < sentences.size()) {
                sentenceLabels.get(i).setText(sentences.get(i));
                sentenceLabels.get(i).setForeground(Color.WHITE);
                java.util.Map<java.awt.font.TextAttribute, Object> attributes = new java.util.HashMap<>();
                attributes.put(java.awt.font.TextAttribute.STRIKETHROUGH, false);
                sentenceLabels.get(i).setFont(sentenceLabels.get(i).getFont().deriveFont(attributes));
            }
        }
        
        centerCardLayout.show(centerPanel, "bonus");
        
        JOptionPane.showMessageDialog(this, "BONUS TIME! 20초간 문장을 입력하여 500점을 획득하세요!", "보너스 타임!", JOptionPane.INFORMATION_MESSAGE);
        
        if (myTeam == Team.YELLOW) {
            yellowInput.requestFocusInWindow();
        } else {
            blueInput.requestFocusInWindow();
        }
    }

    /**
     * (신규) 서버로부터 "문장 입력 결과" 메시지를 받았을 때
     */
    public void handleBonusSentenceResult(boolean success, String sentence, Team team) {
        if (!isBonusTime) return;

        if (success) {
            model.addScore(team, 500);
            yellowScore.setText(model.getScore(Team.YELLOW) + "P");
            blueScore.setText(model.getScore(Team.BLUE) + "P");
        }

        for (JLabel label : sentenceLabels) {
            if (label.getText().equals(sentence)) {
                if (success) {
                    label.setForeground(team == Team.YELLOW ? new Color(0xF2, 0xC1, 0x4E) : new Color(0x5D, 0xA3, 0xFA));
                    
                    java.util.Map<java.awt.font.TextAttribute, Object> attributes = new java.util.HashMap<>();
                    attributes.put(java.awt.font.TextAttribute.STRIKETHROUGH, java.awt.font.TextAttribute.STRIKETHROUGH_ON);
                    label.setFont(label.getFont().deriveFont(attributes));

                    if (team == myTeam) {
                        if (myTeam == Team.YELLOW) yellowInput.setText("");
                        else blueInput.setText("");
                    }
                }
                break;
            }
        }
    }

    /**
     * (신규) 서버로부터 "보너스 타임 종료" 메시지를 받았을 때
     */
    public void handleBonusTimeEnd() {
        isBonusTime = false;
        centerCardLayout.show(centerPanel, "board");
        JOptionPane.showMessageDialog(this, "보너스 타임 종료!", "알림", JOptionPane.INFORMATION_MESSAGE);
    }


    private JLabel flipCounterLabel() {
        JLabel l = new JLabel("0개", SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 18f));
        l.setForeground(Color.DARK_GRAY);
        l.setOpaque(false);
        return l;
    }

    private void refreshFlipLabels() {
        yellowFlipLabel.setText(model.getFlips(Team.YELLOW) + "개");
        blueFlipLabel.setText(model.getFlips(Team.BLUE) + "개");
    }

    private JPanel buildSidePanel(String teamLabel, String playerName, Color tone, ImageIcon emblem, boolean isMine) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        panel.setPreferredSize(new Dimension(170, 0));

        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        if (emblem != null) {
            iconLabel.setIcon(emblem);
        }
        panel.add(iconLabel);
        panel.add(Box.createVerticalStrut(8));

        JLabel teamTitle = new JLabel(teamLabel + (isMine ? " (내 팀)" : ""));
        teamTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        teamTitle.setFont(teamTitle.getFont().deriveFont(Font.BOLD, 16f));
        teamTitle.setForeground(Color.WHITE);

        JLabel nameLabel = new JLabel(playerName, SwingConstants.CENTER);
        nameLabel.setOpaque(true);
        nameLabel.setBackground(new Color(tone.getRed(), tone.getGreen(), tone.getBlue(), 210));
        nameLabel.setForeground(Color.BLACK);
        nameLabel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(teamTitle);
        panel.add(Box.createVerticalStrut(6));
        panel.add(nameLabel);

        return panel;
    }

    private ImageIcon loadScaledIcon(String path, int w, int h) {
        Image img = loadImage(path);
        if (img == null) return null;
        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private Image loadImage(String path) {
        try {
            File f = new File(path);
            if (f.exists()) {
                return ImageIO.read(f);
            }

            File f2 = new File(System.getProperty("user.dir"), path);
            if (f2.exists()) {
                return ImageIO.read(f2);
            }

            java.net.URL res = GameFrame.class.getResource("/" + path);
            if (res != null) {
                return ImageIO.read(res);
            }
        } catch (Exception e) {
            // ignore, fallback to null image
        }
        return null;
    }

}
