import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/**
 * 중앙 보드 패널. 뒤집기 애니메이션을 앞/뒷면 전환처럼 보이도록 조정.
 */
public class BoardPanel extends JPanel {

    private static final int CELL = 56;
    private static final int PAD = 14;
    private static final Color GRID = new Color(0, 0, 0, 40);
    private static final int ANIM_MS = 260;

    private final GameModel model;
    private final NetworkProtocol.Theme theme; // [NEW]
    private final Map<Pos, FlipAnim> animations = new ConcurrentHashMap<>();
    private final javax.swing.Timer animTimer;
    private Image specialImg;

    public BoardPanel(GameModel model, NetworkProtocol.Theme theme) {
        this.model = model;
        this.theme = theme;
        setBackground(new Color(19, 36, 49));
        setOpaque(false);

        // Load special item image
        try {
            specialImg = ImageIO.read(new File("resources/images/treasure_chest.png"));
        } catch (Exception e) {
            System.err.println("Failed to load treasure_chest.png: " + e.getMessage());
        }

        javax.swing.Timer t = new javax.swing.Timer(16, e -> {
            if (animations.isEmpty()) {
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                repaint();
            }
        });
        this.animTimer = t;
    }

    @Override
    public Dimension getPreferredSize() {
        Board b = model.board();
        return new Dimension(PAD * 2 + b.cols() * CELL, PAD * 2 + b.rows() * CELL);
    }

    private Color getTeamColor(Team team) {
        if (team == Team.SPECIAL)
            return new Color(team.rgb);

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
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Board board = model.board();

        float baseFontSize = Math.max(12f, CELL * 0.32f);
        Font baseFont = getFont().deriveFont(Font.BOLD, baseFontSize);
        g.setFont(baseFont);

        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                int x = PAD + c * CELL;
                int y = PAD + r * CELL;

                Cell cell = board.get(r, c);
                Pos pos = new Pos(r, c);
                FlipAnim anim = animations.get(pos);
                double progress = anim == null ? 1.0 : anim.progress();

                // easing
                double eased = (anim == null) ? 1.0 : 0.5 - 0.5 * Math.cos(Math.PI * progress);

                boolean firstHalf = anim != null && progress < 0.5;
                String token = anim != null
                        ? (firstHalf ? anim.fromToken : cell.token())
                        : cell.token();

                Color from = anim != null ? anim.from : getTeamColor(cell.owner());
                Color to = getTeamColor(cell.owner());
                Color drawColor = lerpColor(from, to, eased);

                // Determine which team we are currently rendering
                Team currentRenderTeam;
                if (anim != null) {
                    currentRenderTeam = firstHalf ? anim.fromTeam : cell.owner();
                } else {
                    currentRenderTeam = cell.owner();
                }

                double angle = Math.PI * eased;
                double scaleX = 0.3 + 0.7 * Math.abs(Math.cos(angle)); // 최소 30%까지 축소
                double scaleY = 0.94 + 0.06 * Math.sin(angle); // 살짝 튀어나오는 느낌

                // 텍스트/셀용 스케일은 위 값을 그대로 사용하고,
                // 이미지만 별도로 스케일링하여 텍스트가 커지는 문제 해결
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

                if (currentRenderTeam == Team.SPECIAL && specialImg != null) {
                    int imgW = (int) (CELL * imgScaleX);
                    int imgH = (int) (CELL * imgScaleY);
                    int imgOffsetX = x + (CELL - imgW) / 2;
                    int imgOffsetY = y + (CELL - imgH) / 2;
                    g.drawImage(specialImg, imgOffsetX, imgOffsetY, imgW, imgH, null);
                } else {
                    g.setColor(drawColor);
                    g.fillRoundRect(offsetX, offsetY, w, h, 10, 10);
                }

                // 스페셜 아이템 효과 (반짝임) - 이미지가 있으면 생략
                if (cell.owner() == Team.SPECIAL && specialImg == null) {
                    drawSparkle(g, offsetX, offsetY, w, h);
                }

                // Use smart text fitting
                Layout layout = fitTextToCell(g, baseFont, token, w, h);
                g.setFont(layout.font);
                String drawText = layout.text;

                FontMetrics fm = g.getFontMetrics();

                int tw = fm.stringWidth(drawText);
                int th = fm.getAscent();
                int tx = offsetX + (w - tw) / 2;
                // ascent/descent을 고려해 중앙 정렬(뒤집기 얇은 구간에서도 글자 잘림 방지)
                int ty = offsetY + (h + th - fm.getDescent()) / 2;

                g.setColor(new Color(0, 0, 0, 110));
                g.drawString(drawText, tx + 1, ty + 1);

                // 스페셜이면 글자색을 검정이나 다른색으로?
                if (cell.owner() == Team.SPECIAL) {
                    g.setColor(Color.WHITE); // 흰색
                } else {
                    g.setColor(Color.WHITE);
                }
                g.drawString(drawText, tx, ty);
                g.setFont(baseFont);

                g.setColor(GRID);
                g.drawRoundRect(x, y, CELL, CELL, 10, 10);

                if (anim != null && progress >= 1.0) {
                    animations.remove(pos);
                }
            }
        }
    }

    private record Layout(Font font, String text) {
    }

    private Layout fitTextToCell(Graphics2D g, Font base, String text, int cellW, int cellH) {
        int maxW = Math.max(10, cellW - 8);
        int maxH = Math.max(10, cellH - 4);

        Font f = base;
        // 1. Reduce size (more aggressive, down to 6pt)
        while (f.getSize2D() > 11f) {
            FontMetrics fm = g.getFontMetrics(f);
            if (fm.stringWidth(text) <= maxW && fm.getHeight() <= maxH) {
                break;
            }
            f = f.deriveFont(f.getSize2D() - 1f);
        }

        // 2. Squeeze width
        FontMetrics fm = g.getFontMetrics(f);
        int textW = fm.stringWidth(text);
        if (textW > maxW) {
            double scale = (double) maxW / textW;
            // No limit on compression, just fit it.
            AffineTransform at = new AffineTransform();
            at.scale(scale, 1.0);
            f = f.deriveFont(at);
        }

        return new Layout(f, text);
    }

    public void animateFlips(List<GameModel.FlipResult> flips) {
        long now = System.currentTimeMillis();
        for (GameModel.FlipResult f : flips) {
            animations.put(f.pos(), new FlipAnim(now, getTeamColor(f.from()), f.fromToken(), f.from()));
        }
        if (!flips.isEmpty() && !animTimer.isRunning()) {
            animTimer.start();
        }
    }

    private Color lerpColor(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }

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

    private void drawSparkle(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Color.YELLOW);
        // 간단한 별 모양이나 점 찍기
        long time = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            double offset = (time / 150.0 + i * 2.0) % (Math.PI * 2);
            int sx = x + w / 2 + (int) (Math.cos(offset) * w / 3);
            int sy = y + h / 2 + (int) (Math.sin(offset) * h / 3);
            g.fillOval(sx - 2, sy - 2, 5, 5);
        }
    }
}
