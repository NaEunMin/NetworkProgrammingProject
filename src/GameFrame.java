import javax.swing.*;
import java.awt.*;
import java.io.File;
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

    /**
     * 생성자:
     * 모델, 클라이언트, 내 팀을 외부(GameClient)에서 주입받음.
     */
    public GameFrame(GameModel model, GameClient client, Team myTeam) {
        super("판 뒤집기 (1:1 · 실시간 · Swing) - " + myTeam + "팀");

        this.model = model;
        this.client = client;
        this.myTeam = myTeam;
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

        // 4) 상단 점수판 + 타이머 (사진 레이아웃을 흉내)
        JPanel top = new JPanel(new BorderLayout(8, 8));
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
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        if (myTeam == Team.YELLOW) {
            bottom.add(teamInputPanel("노랑팀", Team.YELLOW, yellowInput, yellowBtn, new Color(241, 209, 109)), BorderLayout.CENTER);
        } else {
            bottom.add(teamInputPanel("파랑팀", Team.BLUE, blueInput, blueBtn, new Color(133, 171, 236)), BorderLayout.CENTER);
        }

        // 6) 프레임 레이아웃 조립
        setLayout(new BorderLayout(8, 8));
        add(top, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

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
        getContentPane().setBackground(new Color(19, 36, 49)); // 전체 배경(바다 느낌)
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
    private JPanel teamInputPanel(String title, Team team, JTextField field, JButton btn, Color tone) {
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

        wrap.add(titleL, BorderLayout.NORTH);
        wrap.add(row, BorderLayout.SOUTH);
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
}
