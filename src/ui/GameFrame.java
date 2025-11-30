package ui;
import javax.swing.*;

import client.IGameClient;
import game.GameModel;
import game.Team;
import protocol.NetworkProtocol;

import java.awt.*;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.sound.sampled.LineEvent;

/**
 * 게임 메인 프레임 (실제 게임 플레이 화면)
 * 
 * [설계]
 * - 상단: 점수판(양 팀) 및 중앙 타이머
 * - 중앙: 게임 보드 (BoardPanel)
 * - 하단: 각 팀의 입력창 및 버튼 (내 팀만 활성화)
 * - 오버레이: 보너스 게임 시 GlassPane을 사용하여 전체 화면 위에 애니메이션 표시
 * 
 * [네트워크 아키텍처]
 * - MVC 패턴: GameModel(데이터) -> GameFrame(뷰) -> GameClient(컨트롤러/네트워크)
 * - 입력 흐름: 사용자 입력 -> handleLocalInput -> GameClient.sendInputRequest -> 서버
 * - 업데이트 흐름: 서버 메시지 -> GameClient 리스너 -> handleRemote*(Tick/Input/GameOver) -> UI 갱신
 * 
 * [주요 기능]
 * - 테마 지원: 생성 시 전달받은 Theme에 따라 배경, 아이콘, 색상 테마 적용
 * - 사운드 효과: 입력 성공, 게임 종료 등 이벤트 발생 시 효과음 재생
 * - 보너스 게임: GlassPane을 활용한 몰입감 있는 오버레이 애니메이션 제공
 */
public class GameFrame extends JFrame {

    // ---- 모델/네트워크 ----
    private final GameModel model;
    private final IGameClient client; // 서버 통신 인터페이스 (멀티: GameClient, 싱글: SingleGameManager)
    private final Team myTeam; // 현재 클라이언트의 팀
    private final NetworkProtocol.Theme theme;

    // ---- UI 구성요소(상단) ----
    private final JLabel yellowScore;
    private final JLabel blueScore;
    private final JLabel timerLabel = new JLabel("01:00", SwingConstants.CENTER);
    private final Image backgroundImage;

    // ---- 중앙 보드 ----
    private final BoardPanel boardPanel;
    private final CardLayout centerCardLayout = new CardLayout();
    private final JPanel centerPanel = new JPanel(centerCardLayout);

    // ---- 보너스 타임 UI ----
    private boolean isBonusTime = false;
    private final BonusGamePanel bonusGamePanel = new BonusGamePanel(); // GlassPane으로 사용

    // ---- 하단 입력(좌/우) ----
    private final JTextField yellowInput = new JTextField(18);
    private final JButton yellowBtn = new JButton("입력하기");
    private final JTextField blueInput = new JTextField(18);
    private final JButton blueBtn = new JButton("입력하기");
    private final JLabel yellowFlipLabel = flipCounterLabel();
    private final JLabel blueFlipLabel = flipCounterLabel();

    /**
     * 생성자: 게임 초기화 및 UI 구성
     * 
     * @param model 초기 게임 모델 (보드 상태 포함)
     * @param client 서버 통신용 클라이언트
     * @param myTeam 내 팀 (입력창 활성화 여부 결정)
     * @param yellowPlayerName 노랑팀(왼쪽) 플레이어 이름
     * @param bluePlayerName 파랑팀(오른쪽) 플레이어 이름
     * @param theme 게임 테마
     */
    public GameFrame(GameModel model, IGameClient client, Team myTeam, String yellowPlayerName, String bluePlayerName,
            NetworkProtocol.Theme theme) {
        super("판 뒤집기 (1:1 · 실시간 · Swing) - " + myTeam + "팀");

        this.model = model;
        this.client = client;
        this.myTeam = myTeam;
        this.theme = theme;

        // 1. 테마 리소스 설정 (기본값: 해적 테마)
        String bgPath = "resources/images/game_background.png";
        Color yellowColor = new Color(0xF2, 0xC1, 0x4E);
        Color blueColor = new Color(0x5D, 0xA3, 0xFA);
        String yellowTeamName = "노랑팀";
        String blueTeamName = "파랑팀";
        String yellowIconPath = "resources/images/yellow_team_pirate.png";
        String blueIconPath = "resources/images/blue_team_pirate.png";

        // 야시장 테마 적용
        if (theme == NetworkProtocol.Theme.NIGHT_MARKET) {
            bgPath = "resources/images/dark_game_background.png";
            yellowColor = new Color(147, 112, 219); // Purple
            blueColor = new Color(255, 165, 0); // Orange
            yellowTeamName = "보라팀";
            blueTeamName = "주황팀";
            yellowIconPath = "resources/images/purple_team_pirate.png";
            blueIconPath = "resources/images/orange_team_pirate.png";
        }

        this.backgroundImage = loadImage(bgPath);
        this.yellowScore = scoreBadge(yellowColor);
        this.blueScore = scoreBadge(blueColor);

        this.boardPanel = new BoardPanel(model, theme);

        // 타이머 초기화
        timerLabel.setText(formatSec(model.secondsLeft()));

        // 2. 중앙 패널 구성 (보드)
        centerPanel.add(boardPanel, "board");
        centerPanel.setOpaque(false);
        
        // 3. 보너스 게임 패널 설정 (GlassPane 사용)
        // GlassPane은 프레임의 최상위 레이어로, 아래 컴포넌트를 가리거나 투과시킬 수 있음
        setGlassPane(bonusGamePanel);
        bonusGamePanel.setVisible(false);

        // 4. 상단 패널 (점수판 + 타이머)
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        yellowScore.setText(model.getScore(Team.YELLOW) + "P");
        blueScore.setText(model.getScore(Team.BLUE) + "P");

        timerLabel.setFont(timerLabel.getFont().deriveFont(Font.BOLD, 22f));
        timerLabel.setForeground(Color.WHITE);
        JPanel timerWrap = pill(timerLabel, new Color(26, 47, 60));

        top.add(pill(yellowScore, yellowColor), BorderLayout.WEST);
        top.add(timerWrap, BorderLayout.CENTER);
        top.add(pill(blueScore, blueColor), BorderLayout.EAST);

        // 5. 하단 패널 (입력창)
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel inputPanel;
        if (myTeam == Team.YELLOW) {
            inputPanel = teamInputPanel(yellowTeamName, Team.YELLOW, yellowInput, yellowBtn, yellowFlipLabel,
                    yellowColor);
        } else {
            inputPanel = teamInputPanel(blueTeamName, Team.BLUE, blueInput, blueBtn, blueFlipLabel, blueColor);
        }
        // 입력창 너비를 보드 너비에 맞춤
        int desiredWidth = boardPanel.getPreferredSize().width + 40;
        inputPanel.setPreferredSize(new Dimension(desiredWidth, inputPanel.getPreferredSize().height));
        bottom.add(inputPanel);
        // bottom.add(inputPanel); // 중복 추가 제거 (원래 코드에 있었으나 불필요해 보임, 하지만 레이아웃 균형을 위해 더미가 필요할 수도 있음. 일단 하나만 추가)
        refreshFlipLabels();

        // 보너스 게임 패널에 입력 영역 위치 전달 (오버레이 구멍 뚫기용)
        bonusGamePanel.setInputArea(inputPanel);

        ImageIcon yellowTeamIcon = loadScaledIcon(yellowIconPath, 140, 180);
        ImageIcon blueTeamIcon = loadScaledIcon(blueIconPath, 140, 180);

        // 6. 전체 레이아웃 조립
        JPanel middle = new JPanel(new BorderLayout(8, 0));
        middle.setOpaque(false);
        middle.add(buildSidePanel(yellowTeamName, yellowPlayerName, yellowColor, yellowTeamIcon,
                myTeam == Team.YELLOW), BorderLayout.WEST);
        middle.add(centerPanel, BorderLayout.CENTER);
        middle.add(buildSidePanel(blueTeamName, bluePlayerName, blueColor, blueTeamIcon, myTeam == Team.BLUE),
                BorderLayout.EAST);

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

        // 7. 이벤트 리스너 등록
        // 입력 버튼/엔터 키 -> handleLocalInput 호출
        yellowBtn.addActionListener(e -> handleLocalInput(Team.YELLOW, yellowInput));
        yellowInput.addActionListener(e -> handleLocalInput(Team.YELLOW, yellowInput));
        blueBtn.addActionListener(e -> handleLocalInput(Team.BLUE, blueInput));
        blueInput.addActionListener(e -> handleLocalInput(Team.BLUE, blueInput));

        // 8. 내 팀이 아닌 입력창 비활성화
        if (myTeam == Team.YELLOW) {
            blueInput.setEnabled(false);
            blueBtn.setEnabled(false);
        } else {
            yellowInput.setEnabled(false);
            yellowBtn.setEnabled(false);
        }

        // 9. 윈도우 설정
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    /** 점수 배지 UI 생성 */
    private static JLabel scoreBadge(Color fg) {
        JLabel l = new JLabel("0P", SwingConstants.CENTER);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 20f));
        l.setForeground(Color.BLACK);
        return l;
    }

    /** 둥근 캡슐 모양 배경 패널 생성 */
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

    /** 팀별 입력 패널 생성 (뒤집은 횟수 + 입력창 + 버튼) */
    private JPanel teamInputPanel(String title, Team team, JTextField field, JButton btn, JLabel flipLabel,
            Color tone) {
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
        JPanel flipCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(255, 255, 255, 210));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        flipCard.setOpaque(false);
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
     * 로컬 사용자 입력 처리
     * 1. 유효성 검사 (내 팀 여부, 시간 종료 여부, 빈 입력)
     * 2. 보너스 타임 여부에 따라 다른 메시지 전송
     */
    private void handleLocalInput(Team team, JTextField field) {
        if (team != myTeam || model.secondsLeft() <= 0)
            return;

        String input = field.getText().trim();
        if (input == null || input.isBlank())
            return;

        // 일반 게임 중에는 4글자 제한 (보너스 타임 제외)
        if (input.length() > 4 && !isBonusTime) {
            return;
        }

        if (isBonusTime) {
            client.sendSentenceInput(team, input);
        } else {
            client.sendInputRequest(team, input);
        }
    }

    /** 게임 종료 시 입력 비활성화 */
    private void disableInputs() {
        for (var c : new JComponent[] { yellowInput, yellowBtn, blueInput, blueBtn })
            c.setEnabled(false);
    }

    /** 초 단위를 MM:SS 형식으로 변환 */
    private static String formatSec(int sec) {
        int m = Math.max(0, sec) / 60;
        int s = Math.max(0, sec) % 60;
        return String.format("%02d:%02d", m, s);
    }

    /** 사운드 재생 (비동기 스레드) */
    private void playSound(String soundFileName) {
        new Thread(() -> {
            try (AudioInputStream audioIn = AudioSystem
                    .getAudioInputStream(new File("resources/sounds/" + soundFileName))) {
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

    // --- 서버 메시지 핸들러 (GameClient에서 호출) ---

    /**
     * 서버로부터 Tick(1초 경과) 수신 시 호출
     */
    public void handleRemoteTick() {
        model.tickOneSecond();
        timerLabel.setText(formatSec(model.secondsLeft()));
    }

    /**
     * 서버로부터 입력 처리 결과 수신 시 호출
     * - 보드 업데이트 (애니메이션)
     * - 점수 및 뒤집기 횟수 갱신
     * - 효과음 재생
     * - 입력창 초기화 (성공 시)
     */
    public void handleRemoteInput(Team team, String input) {
        if (model.secondsLeft() <= 0)
            return;

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
                myField.selectAll(); // 실패 시 전체 선택 (빠른 재입력 유도)
            } else {
                myField.setText(""); // 성공 시 초기화
            }
        }
    }

    public void repaintBoard() {
        boardPanel.repaint();
    }

    /**
     * 서버로부터 게임 종료 수신 시 호출
     * - 결과 팝업 표시
     * - 입력 차단
     */
    public void handleRemoteGameOver() {
        playSound("finish.wav");
        disableInputs();

        int y = model.getScore(Team.YELLOW);
        int b = model.getScore(Team.BLUE);
        String msg = (y == b) ? "비겼습니다!"
                : (y > b ? "노랑팀 승리!" : "파랑팀 승리!");
        JOptionPane.showMessageDialog(this, msg + "  (노랑 " + y + " / 파랑 " + b + ")", "게임 종료",
                JOptionPane.INFORMATION_MESSAGE);
        client.gameHasFinished();
    }

    // --- 보너스 타임 핸들러 ---

    /**
     * 보너스 타임 시작
     * - GlassPane(BonusGamePanel) 활성화 및 애니메이션 시작
     */
    public void handleBonusTimeStart(java.util.List<String> sentences) {
        isBonusTime = true;

        bonusGamePanel.setVisible(true);
        bonusGamePanel.startBonusTime(sentences);

        if (myTeam == Team.YELLOW) {
            yellowInput.requestFocusInWindow();
        } else {
            blueInput.requestFocusInWindow();
        }
    }

    /**
     * 보너스 문장 입력 결과 처리
     * - 성공 시 점수 추가 및 사슬 끊기 애니메이션 트리거
     */
    public void handleBonusSentenceResult(boolean success, String sentence, Team team) {
        System.out.println("GameFrame: Bonus Result - Success=" + success + ", Team=" + team + ", Sentence='" + sentence + "'");
        if (!isBonusTime) {
            return;
        }

        if (success) {
            model.addScore(team, 500);
            yellowScore.setText(model.getScore(Team.YELLOW) + "P");
            blueScore.setText(model.getScore(Team.BLUE) + "P");
            
            // 사슬 끊기 효과
            bonusGamePanel.solveSentence(sentence);
            
            if (team == myTeam) {
                if (myTeam == Team.YELLOW)
                    yellowInput.setText("");
                else
                    blueInput.setText("");
            }
        }
    }

    /**
     * 보너스 타임 종료
     * - 퇴장 애니메이션 후 GlassPane 비활성화
     */
    public void handleBonusTimeEnd() {
        isBonusTime = false;
        bonusGamePanel.endBonusTime();
        
        // 퇴장 애니메이션 시간(약 2초) 후 패널 숨김
        Timer t = new Timer(2000, e -> {
             bonusGamePanel.setVisible(false);
        });
        t.setRepeats(false);
        t.start();
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

    /** 좌/우측 플레이어 정보 패널 생성 */
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
        if (img == null)
            return null;
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
            // ignore
        }
        return null;
    }
}
