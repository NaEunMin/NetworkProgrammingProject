import javax.swing.*;
import java.awt.*;
import java.io.File;
import javax.sound.sampled.*;
import javax.sound.sampled.LineEvent;

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

        // 4) 상단 점수판 + 타이머 (사진 레이아웃을 흉내)
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        yellowScore.setText(model.count(Team.YELLOW) + "P");
        blueScore.setText(model.count(Team.BLUE) + "P");

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
        add(boardPanel, BorderLayout.CENTER);
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
                // super.paintComponent(g)는 둥근 사각형 위에 컴포넌트를 그리기 위해
                // 여기서는 호출하지 않거나, setOpaque(false) 후 호출해야 합니다.
                // 하지만 inner 컴포넌트가 알아서 그려지므로 아래처럼 Opaque(false)만 둡니다.
            }
        };
        // inner 컴포넌트가 배경을 가리지 않도록 pill 패널 자체는 투명하게
        p.setOpaque(false); 
        inner.setOpaque(false); // 내부 컴포넌트도 투명해야 배경색이 보임
        
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
                // super.paintComponent(g) 호출 안함 (위와 동일 이유)
            }
        };
        wrap.setOpaque(false); // 둥근 배경을 위해 패널 자체는 투명하게
        wrap.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JPanel row = new JPanel(new BorderLayout(6, 6));
        row.setOpaque(false); // 내부 패널도 투명하게
        row.add(field, BorderLayout.CENTER);
        row.add(btn, BorderLayout.EAST);

        wrap.add(titleL, BorderLayout.NORTH);
        wrap.add(row, BorderLayout.SOUTH);
        return wrap;
    }

    /**
     * (7) 로컬 입력 처리
     * - 내 팀이 아니거나 시간이 0이면 무시.
     * - GameModel을 직접 수정하는 대신, GameClient를 통해 서버로 "입력 요청" 전송.
     */
    private void handleLocalInput(Team team, JTextField field) {
        if (team != myTeam || model.secondsLeft() <= 0) return;

        String input = field.getText();
        if (input == null || input.isBlank()) return;

        // 서버로 입력 메시지 전송
        client.sendInputRequest(team, input);
        
        // (중요) UI 갱신은 여기서 하지 않음.
        // 서버가 브로드캐스트하는 "처리 결과"를 받아 갱신 (handleRemoteInput에서)
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
        // 모델 상태 갱신
        model.tickOneSecond(); 
        // UI 갱신
        timerLabel.setText(formatSec(model.secondsLeft())); 
    }

    /**
     * (신규) 서버로부터 "입력 처리" 명령을 받았을 때 (EDT에서 호출 보장)
     */
    public void handleRemoteInput(Team team, String input) {
        if (model.secondsLeft() <= 0) return;

        // 모든 클라이언트가 자신의 로컬 모델을 "동일하게" 갱신 (Lock-step)
        java.util.List<GameModel.FlipResult> flips = model.flipByInput(team, input);
        
        if (!flips.isEmpty()) {
            playSound("bell.wav");
        }

        // UI 갱신
        boardPanel.animateFlips(flips);
        yellowScore.setText(model.count(Team.YELLOW) + "P");
        blueScore.setText(model.count(Team.BLUE) + "P");

        // 피드백: "내"가 입력한 것이었다면 입력창 처리
        if (team == myTeam) {
            JTextField myField = (myTeam == Team.YELLOW) ? yellowInput : blueInput;
            if (flips.isEmpty()) {
                myField.selectAll(); // 실패 시 텍스트 선택
            } else {
                myField.setText(""); // 성공 시 비우기
            }
        }
    }

    /**
     * (신규) 서버로부터 "게임 종료" 메시지를 받았을 때 (EDT에서 호출 보장)
     */
    public void handleRemoteGameOver() {
        playSound("finish.wav");
        disableInputs();
        
        // 최종 결과 계산 및 표시
        int y = model.count(Team.YELLOW);
        int b = model.count(Team.BLUE);
        String msg = (y == b) ? "비겼습니다!"
                : (y > b ? "노랑팀 승리!" : "파랑팀 승리!");
        JOptionPane.showMessageDialog(this, msg + "  (노랑 " + y + " / 파랑 " + b + ")", "게임 종료", JOptionPane.INFORMATION_MESSAGE);
    }
}
