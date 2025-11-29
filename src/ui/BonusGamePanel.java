package ui;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

public class BonusGamePanel extends JPanel {

    // 애니메이션 상태 정의
    private enum State {
        IDLE,           // 대기 상태
        SHIP_ENTERING,  // 해적선 등장 (왼쪽 -> 중앙)
        CHAINS_DROPPING,// 사슬이 내려오는 중
        ACTIVE,         // 게임 진행 중 (문장 입력 대기)
        EXITING         // 게임 종료 후 퇴장 (중앙 -> 오른쪽)
    }

    private State state = State.IDLE;
    private final Timer animTimer;
    
    // Assets (이미지 리소스)
    private BufferedImage shipImage;
    private BufferedImage chainImage;
    
    // Animation Variables (애니메이션 좌표 및 속성)
    private double shipX; // 배의 현재 X 좌표
    private double targetShipX; // 배가 멈출 목표 X 좌표 (화면 중앙)
    private double[] chainY; // 각 사슬의 현재 Y 길이
    private double targetChainY; // 사슬이 다 내려왔을 때의 목표 Y 길이
    
    private List<String> sentences = new ArrayList<>();
    private boolean[] solved; // 각 문장의 해결 여부 (true면 사슬이 끊어짐)
    
    private static final int SHIP_WIDTH = 500; // [MODIFIED] 배 크기 확대
    private static final int SHIP_HEIGHT = 350;
    private static final int CHAIN_WIDTH = 30;
    
    public BonusGamePanel() {
        setOpaque(false); // 배경을 투명하게 설정하여 오버레이 효과 구현
        loadAssets();
        
        // 16ms마다 화면 갱신 (약 60FPS)
        animTimer = new Timer(16, e -> updateAnimation());
    }
    
    private void loadAssets() {
        try {
            shipImage = loadImage("resources/images/pirate_ship.png");
            chainImage = loadImage("resources/images/pirate_chain.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private BufferedImage loadImage(String path) {
        try {
            File f = new File(path);
            if (f.exists()) return ImageIO.read(f);
            return ImageIO.read(getClass().getResource("/" + path));
        } catch (Exception e) {
            return null;
        }
    }
    
    // [NEW] 폭발 효과를 위한 파티클 시스템
    // 사슬이 끊어질 때 불꽃이 튀는 효과를 연출합니다.
    private static class Particle {
        double x, y;
        double vx, vy;
        Color color;
        float life; // 생명주기 (1.0 -> 0.0)

        Particle(double x, double y) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 5;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.life = 1.0f;
            
            // 불꽃 색상 랜덤 생성 (주황~노랑 계열)
            int r = 200 + (int)(Math.random() * 55);
            int g = 100 + (int)(Math.random() * 100);
            int b = 0;
            this.color = new Color(r, g, b);
        }
    }

    private List<Particle> particles = new ArrayList<>();
    private double[] chainVelocity; // 끊어진 사슬의 낙하 속도
    private boolean[] isFalling;    // 사슬이 끊어져서 떨어지고 있는지 여부

    /**
     * 보너스 타임 시작: 초기화 및 등장 애니메이션 시작
     * @param sentences 서버로부터 받은 보너스 문장 리스트
     */
    public void startBonusTime(List<String> sentences) {
        this.sentences = new ArrayList<>(sentences);
        this.solved = new boolean[sentences.size()];
        this.state = State.SHIP_ENTERING;
        
        // 초기 위치 설정 (화면 왼쪽 밖)
        this.shipX = -SHIP_WIDTH; 
        this.targetShipX = (getWidth() - SHIP_WIDTH) / 2.0;
        
        this.chainY = new double[sentences.size()];
        this.chainVelocity = new double[sentences.size()];
        this.isFalling = new boolean[sentences.size()];
        
        for(int i=0; i<chainY.length; i++) {
            chainY[i] = 10 + SHIP_HEIGHT / 2; // 사슬 시작점 (배의 중앙)
            chainVelocity[i] = 0;
            isFalling[i] = false;
        }
        
        // 사슬 목표 길이 설정 (배 밑으로 내려오도록)
        this.targetChainY = 10 + SHIP_HEIGHT + 20;
        
        animTimer.start();
        repaint();
    }
    
    /**
     * 정답 처리: 해당 문장의 사슬을 끊고 폭발 효과를 발생시킵니다.
     * @param sentence 맞춘 문장
     */
    public void solveSentence(String sentence) {
        System.out.println("BonusGamePanel: Trying to solve '" + sentence + "'");
        // 입력값 정규화 (NFC) 및 공백 제거로 매칭 정확도 향상
        String normalizedInput = java.text.Normalizer.normalize(sentence.trim(), java.text.Normalizer.Form.NFC);

        for (int i = 0; i < sentences.size(); i++) {
            if (solved[i]) continue;
            
            String target = sentences.get(i);
            // 타겟 문장도 동일하게 정규화
            String normalizedTarget = java.text.Normalizer.normalize(target.trim(), java.text.Normalizer.Form.NFC);

            if (normalizedInput.equals(normalizedTarget)) {
                solved[i] = true;
                
                // [NEW] 사슬 끊김 처리 및 낙하 시작
                isFalling[i] = true;
                
                // 폭발 위치 계산 (텍스트 상자 상단 연결부)
                int startX = (int)shipX + 50;
                int gap = (SHIP_WIDTH - 100) / Math.max(1, sentences.size());
                int x = startX + i * gap;
                int y = (int)chainY[i]; 
                
                // 파티클 생성 (폭발 효과)
                explode(x, y - 10);
                
                repaint();
                break;
            }
        }
    }
    
    private void explode(int x, int y) {
        for(int i=0; i<30; i++) {
            particles.add(new Particle(x, y));
        }
    }
    
    public void endBonusTime() {
        this.state = State.EXITING;
        // 배가 오른쪽으로 퇴장하도록 상태 변경
    }
    
    /**
     * 애니메이션 업데이트 루프 (매 프레임 호출)
     * 상태에 따라 배 이동, 사슬 낙하, 파티클 업데이트 등을 수행합니다.
     */
    private void updateAnimation() {
        // 1. 파티클 업데이트 (수명 감소 및 이동)
        for(int i=0; i<particles.size(); i++) {
            Particle p = particles.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.2; // 중력 적용
            p.life -= 0.03f; // 서서히 사라짐
            if(p.life <= 0) {
                particles.remove(i);
                i--;
            }
        }

        // [FIX] 끊어진 사슬은 상태와 무관하게 항상 중력 적용 (즉시 낙하)
        for(int i=0; i<chainY.length; i++) {
            if(isFalling[i]) {
                chainVelocity[i] += 1.0; // 중력 가속도
                chainY[i] += chainVelocity[i];
            }
        }

        if (state == State.SHIP_ENTERING) {
            // Ease-out 효과로 부드럽게 등장
            double diff = targetShipX - shipX;
            shipX += diff * 0.05;
            
            if (Math.abs(diff) < 1.0) {
                shipX = targetShipX;
                state = State.CHAINS_DROPPING;
            }
        } else if (state == State.CHAINS_DROPPING) {
            boolean allDown = true;
            for (int i = 0; i < chainY.length; i++) {
                // 이미 끊어진 사슬은 드롭 애니메이션에서 제외
                if (isFalling[i]) continue;

                // [MODIFIED] 사슬마다 길이를 다르게 하여 시각적 단조로움 탈피
                double myTarget = targetChainY + ((i * 90) % 250);
                
                double diff = myTarget - chainY[i];
                chainY[i] += diff * 0.1; // 부드럽게 내려옴
                if (Math.abs(diff) > 1.0) allDown = false;
            }
            if (allDown) {
                state = State.ACTIVE;
            }
        } else if (state == State.ACTIVE) {
            // 게임 진행 중 (별도 로직 없음, 입력 대기)
        } else if (state == State.EXITING) {
            shipX += 10; // 오른쪽으로 이동하여 퇴장
            if (shipX > getWidth()) { 
                state = State.IDLE;
                animTimer.stop();
            }
        }
        repaint();
    }
    
    private JComponent inputArea;

    public void setInputArea(JComponent inputArea) {
        this.inputArea = inputArea;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (state == State.IDLE) return;
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // [RESTORED] 배경 오버레이 그리기 (입력창 제외)
        // 전체 화면을 어둡게 하되, 입력창 부분만 구멍을 뚫어 강조 효과를 줍니다.
        g2.setColor(new Color(0, 0, 0, 200)); 
        
        if (inputArea != null && inputArea.isShowing()) {
            // 전체 화면 영역 생성
            java.awt.geom.Area overlayArea = new java.awt.geom.Area(new Rectangle(0, 0, getWidth(), getHeight()));
            
            // 입력창의 위치를 현재 패널 기준으로 변환
            Point pt = SwingUtilities.convertPoint(inputArea, 0, 0, this);
            Rectangle inputRect = new Rectangle(pt.x, pt.y, inputArea.getWidth(), inputArea.getHeight());
            
            // 전체 영역에서 입력창 영역을 뺌 (구멍 뚫기)
            overlayArea.subtract(new java.awt.geom.Area(inputRect));
            
            g2.fill(overlayArea);
        } else {
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // 배 그리기
        if (shipImage != null) {
            g2.drawImage(shipImage, (int)shipX, 10, SHIP_WIDTH, SHIP_HEIGHT, null); 
        } else {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect((int)shipX, 10, SHIP_WIDTH, SHIP_HEIGHT);
        }
        
        // 사슬 및 문장 상자 그리기
        if (state != State.SHIP_ENTERING) {
            int startX = (int)shipX + 50;
            int gap = (SHIP_WIDTH - 100) / Math.max(1, sentences.size());
            
            for (int i = 0; i < sentences.size(); i++) {
                // 이미 해결되어 떨어지고 있는 사슬은 아래 로직에서 처리됨
                // (solved && !isFalling)인 경우는 없어야 함
                
                int x = startX + i * gap;
                int y = (int)chainY[i];
                
                // 사슬 그리기 (끊어지지 않은 경우)
                if (!isFalling[i]) {
                    if (chainImage != null) {
                        g2.drawImage(chainImage, x - CHAIN_WIDTH/2, 10 + SHIP_HEIGHT/2, CHAIN_WIDTH, y - (10 + SHIP_HEIGHT/2), null);
                    } else {
                        g2.setColor(Color.GRAY);
                        g2.setStroke(new BasicStroke(3));
                        g2.drawLine(x, 10 + SHIP_HEIGHT/2, x, y);
                    }
                } else {
                    // [NEW] 끊어진 사슬 잔해 그리기 (상자에 매달린 짧은 사슬)
                     if (chainImage != null) {
                        g2.drawImage(chainImage, x - CHAIN_WIDTH/2, y - 30, CHAIN_WIDTH, 30, null);
                    }
                }
                
                // 문장 텍스트 박스 그리기
                String text = sentences.get(i);
                g2.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(text);
                int th = fm.getHeight();
                
                int boxW = tw + 20;
                int boxH = th + 10;
                
                g2.setColor(new Color(0, 0, 0, 180));
                g2.fillRoundRect(x - boxW/2, y, boxW, boxH, 10, 10);
                g2.setColor(Color.WHITE);
                g2.drawString(text, x - tw/2, y + fm.getAscent() + 5);
            }
        }
        
        // [NEW] 파티클(불꽃) 그리기
        for(Particle p : particles) {
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(p.life * 255)));
            g2.fillOval((int)p.x, (int)p.y, 6, 6);
        }
    }
}
