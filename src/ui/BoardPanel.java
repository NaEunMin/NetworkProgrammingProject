package ui;
import javax.swing.*;

import game.Board;
import game.Cell;
import game.GameModel;
import game.Pos;
import game.Team;
import protocol.NetworkProtocol;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import java.io.File;

/**
 * 게임 보드 렌더링 및 애니메이션 패널
 * 
 * [설계]
 * - JPanel의 paintComponent를 오버라이드하여 커스텀 그래픽 구현
 * - 60fps 타이머를 사용하여 부드러운 뒤집기 애니메이션 처리
 * - 텍스트 자동 크기 조절(fitTextToCell)로 다양한 길이의 단어 대응
 * 
 * [시각 효과]
 * - 뒤집기 애니메이션: 코사인 함수를 이용한 3D 회전 효과 (scaleX 조절)
 * - 색상 보간(Lerp): 팀 색상 간 부드러운 전환
 * - 스페셜 아이템: 이미지 렌더링 및 발광(Glow)/반짝임(Sparkle) 효과
 */
public class BoardPanel extends JPanel {

    private static final int CELL = 56; // 셀 크기
    private static final int PAD = 14;  // 패딩
    private static final Color GRID = new Color(0, 0, 0, 40); // 격자 색상
    private static final int ANIM_MS = 260; // 애니메이션 지속 시간

    private final GameModel model;
    private final NetworkProtocol.Theme theme;
    
    // 진행 중인 애니메이션 관리 (Thread-safe Map)
    private final Map<Pos, FlipAnim> animations = new ConcurrentHashMap<>();
    private final javax.swing.Timer animTimer;
    
    private Image specialImg;

    public BoardPanel(GameModel model, NetworkProtocol.Theme theme) {
        this.model = model;
        this.theme = theme;
        setBackground(new Color(19, 36, 49));
        setOpaque(false); // 배경 투명 처리 (상위 패널 배경 사용)

        // 스페셜 아이템 이미지 로드
        // 스페셜 아이템 이미지 로드 (파일 -> 리소스 순)
        try {
            File f = new File("resources/images/treasure_chest.png");
            if (f.exists()) {
                specialImg = ImageIO.read(f);
            } else {
                java.net.URL res = getClass().getResource("/resources/images/treasure_chest.png");
                if (res != null) {
                    specialImg = ImageIO.read(res);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load treasure_chest.png: " + e.getMessage());
        }

        // 애니메이션 타이머 (약 60fps)
        javax.swing.Timer t = new javax.swing.Timer(16, e -> {
            if (animations.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                repaint(); // 애니메이션이 있으면 다시 그리기
            }
        });
        this.animTimer = t;
    }

    @Override
    public Dimension getPreferredSize() {
        Board b = model.board();
        return new Dimension(PAD * 2 + b.cols() * CELL, PAD * 2 + b.rows() * CELL);
    }

    /**
     * 팀별 색상 반환 (테마 적용)
     */
    private Color getTeamColor(Team team) {
        if (team == Team.SPECIAL)
            return Color.BLACK;

        if (theme == NetworkProtocol.Theme.NIGHT_MARKET) {
            if (team == Team.YELLOW)
                return new Color(147, 112, 219); // Purple
            if (team == Team.BLUE)
                return new Color(255, 165, 0); // Orange
        }
        return new Color(team.rgb);
    }

    @Override
    protected void paintComponent(Graphics raw) {
        super.paintComponent(raw);
        Graphics2D g = (Graphics2D) raw;
        // 안티앨리어싱 설정 (부드러운 그래픽)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Board board = model.board();

        float baseFontSize = Math.max(12f, CELL * 0.32f);
        Font baseFont = getFont().deriveFont(Font.BOLD, baseFontSize);
        g.setFont(baseFont);

        // 모든 셀 순회하며 렌더링
        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                int x = PAD + c * CELL;
                int y = PAD + r * CELL;

                Cell cell = board.get(r, c);
                Pos pos = new Pos(r, c);
                FlipAnim anim = animations.get(pos);
                
                // 애니메이션 진행도 (0.0 ~ 1.0)
                double progress = anim == null ? 1.0 : anim.progress();

                // Easing 함수 적용 (부드러운 감속/가속)
                double eased = (anim == null) ? 1.0 : 0.5 - 0.5 * Math.cos(Math.PI * progress);

                // 애니메이션 전반부(0.0~0.5)는 이전 상태, 후반부(0.5~1.0)는 현재 상태 표시
                boolean firstHalf = anim != null && progress < 0.5;
                String token = anim != null
                        ? (firstHalf ? anim.fromToken : cell.token())
                        : cell.token();

                // 색상 보간
                Color from = anim != null ? anim.from : getTeamColor(cell.owner());
                Color to = getTeamColor(cell.owner());
                Color drawColor = lerpColor(from, to, eased);

                // 현재 렌더링할 팀 결정
                Team currentRenderTeam;
                if (anim != null) {
                    currentRenderTeam = firstHalf ? anim.fromTeam : cell.owner();
                } else {
                    currentRenderTeam = cell.owner();
                }

                // 3D 회전 효과 계산 (scaleX를 줄여서 회전하는 것처럼 보이게 함)
                double angle = Math.PI * eased;
                double scaleX = 0.3 + 0.7 * Math.abs(Math.cos(angle)); // 최소 30%까지 축소
                double scaleY = 0.94 + 0.06 * Math.sin(angle); // 살짝 튀어나오는 느낌

                // 이미지 스케일링 별도 처리 (스페셜 아이템은 좀 더 크게)
                double imgScaleX = scaleX;
                double imgScaleY = scaleY;

                if (currentRenderTeam == Team.SPECIAL) {
                    imgScaleX = 2.0 + 0.06 * Math.sin(angle);
                    imgScaleY = 2.0 + 0.06 * Math.sin(angle);
                }

                int w = (int) (CELL * scaleX);
                int h = (int) (CELL * scaleY);
                int offsetX = x + (CELL - w) / 2;
                int offsetY = y + (CELL - h) / 2;

                // 그리기: 스페셜 아이템(이미지) vs 일반 셀(Round Rect)
                if (currentRenderTeam == Team.SPECIAL) {
                    if (specialImg != null) {
                        int imgW = (int) (CELL * imgScaleX);
                        int imgH = (int) (CELL * imgScaleY);
                        int imgOffsetX = x + (CELL - imgW) / 2;
                        int imgOffsetY = y + (CELL - imgH) / 2;
                        g.drawImage(specialImg, imgOffsetX, imgOffsetY, imgW, imgH, null);
                    } else {
                        // Fallback: 이미지가 없으면 노란색 원으로 표시
                        g.setColor(Color.YELLOW);
                        g.fillOval(offsetX, offsetY, w, h);
                    }
                } else {
                    g.setColor(drawColor);
                    g.fillRoundRect(offsetX, offsetY, w, h, 10, 10);
                }

                // 스페셜 아이템 효과 (발광 + 반짝임)
                if (cell.owner() == Team.SPECIAL) {
                    drawGlowingBorder(g, offsetX, offsetY, w, h);
                    drawSparkle(g, offsetX, offsetY, w, h);
                }

                // 텍스트 렌더링 (자동 크기 조절)
                Layout layout = fitTextToCell(g, baseFont, token, w, h);
                g.setFont(layout.font);
                String drawText = layout.text;

                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(drawText);
                int th = fm.getAscent();
                int tx = offsetX + (w - tw) / 2;
                int ty = offsetY + (h + th - fm.getDescent()) / 2;

                // 텍스트 그림자
                g.setColor(new Color(0, 0, 0, 110));
                g.drawString(drawText, tx + 1, ty + 1);

                // 텍스트 본문
                g.setColor(Color.WHITE);
                g.drawString(drawText, tx, ty);
                g.setFont(baseFont);

                // 격자 테두리
                g.setColor(GRID);
                g.drawRoundRect(x, y, CELL, CELL, 10, 10);

                // 애니메이션 종료 처리
                if (anim != null && progress >= 1.0) {
                    animations.remove(pos);
                }
            }
        }
    }

    private record Layout(Font font, String text) {
    }

    /**
     * 텍스트가 셀 안에 들어오도록 폰트 크기 조절 및 장평 조절
     */
    private Layout fitTextToCell(Graphics2D g, Font base, String text, int cellW, int cellH) {
        int maxW = Math.max(10, cellW - 8);
        int maxH = Math.max(10, cellH - 4);

        Font f = base;
        // 1. 폰트 크기 줄이기 (최소 11pt까지)
        while (f.getSize2D() > 11f) {
            FontMetrics fm = g.getFontMetrics(f);
            if (fm.stringWidth(text) <= maxW && fm.getHeight() <= maxH) {
                break;
            }
            f = f.deriveFont(f.getSize2D() - 1f);
        }

        // 2. 그래도 넘치면 장평(가로 비율) 축소
        FontMetrics fm = g.getFontMetrics(f);
        int textW = fm.stringWidth(text);
        if (textW > maxW) {
            double scale = (double) maxW / textW;
            AffineTransform at = new AffineTransform();
            at.scale(scale, 1.0);
            f = f.deriveFont(at);
        }

        return new Layout(f, text);
    }

    /**
     * 뒤집기 애니메이션 시작
     */
    public void animateFlips(List<GameModel.FlipResult> flips) {
        long now = System.currentTimeMillis();
        for (GameModel.FlipResult f : flips) {
            animations.put(f.pos(), new FlipAnim(now, getTeamColor(f.from()), f.fromToken(), f.from()));
        }
        if (!flips.isEmpty() && !animTimer.isRunning()) {
            animTimer.start();
        }
    }

    /**
     * 색상 선형 보간 (Linear Interpolation)
     */
    private Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }

    /**
     * 애니메이션 상태 객체
     */
    private class FlipAnim {
        final long startMs;
        final Color from;
        final String fromToken;
        final Team fromTeam;

        FlipAnim(long startMs, Color from, String fromToken, Team fromTeam) {
            this.startMs = startMs;
            this.from = from;
            this.fromToken = fromToken;
            this.fromTeam = fromTeam;
        }

        double progress() {
            double t = (System.currentTimeMillis() - startMs) / (double) ANIM_MS;
            return Math.min(1.0, t);
        }
    }

    /**
     * 스페셜 아이템 발광 효과 (Glow)
     * 반투명한 사각형을 여러 겹 그려서 빛이 퍼지는 느낌 연출
     */
    private void drawGlowingBorder(Graphics2D g, int x, int y, int w, int h) {
        for (int i = 1; i <= 5; i++) {
            int alpha = Math.max(0, 150 - (i * 25));
            g.setColor(new Color(255, 215, 0, alpha)); // Gold color
            g.drawRoundRect(x - i, y - i, w + (i * 2), h + (i * 2), 14, 14);
        }
    }

    /**
     * 스페셜 아이템 반짝임 효과 (Sparkle)
     * 시간 기반으로 회전하는 점들을 그려서 반짝이는 느낌 연출
     */
    private void drawSparkle(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Color.YELLOW);
        long time = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            double offset = (time / 150.0 + i * 2.0) % (Math.PI * 2);
            int sx = x + w / 2 + (int) (Math.cos(offset) * w / 3);
            int sy = y + h / 2 + (int) (Math.sin(offset) * h / 3);
            g.fillOval(sx - 2, sy - 2, 5, 5);
        }
    }
}
