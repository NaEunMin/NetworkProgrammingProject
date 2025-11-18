import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 중앙 보드 패널. 뒤집기 애니메이션을 앞/뒷면 전환처럼 보이도록 조정.
 */
public class BoardPanel extends JPanel {

    private static final int CELL   = 56;
    private static final int PAD    = 14;
    private static final Color GRID = new Color(0, 0, 0, 40);
    private static final int ANIM_MS = 260;

    private final GameModel model;
    private final Map<Pos, FlipAnim> animations = new ConcurrentHashMap<>();
    private final javax.swing.Timer animTimer;

    public BoardPanel(GameModel model) {
        this.model = model;
        setBackground(new Color(19, 36, 49));
        setOpaque(true);

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

                Color from = anim != null ? anim.from : new Color(cell.owner().rgb);
                Color to   = new Color(cell.owner().rgb);
                Color drawColor = lerpColor(from, to, eased);

                double angle = Math.PI * eased;
                double scaleX = 0.3 + 0.7 * Math.abs(Math.cos(angle)); // 최소 30%까지 축소
                double scaleY = 0.94 + 0.06 * Math.sin(angle);        // 살짝 튀어나오는 느낌
                int w = (int) (CELL * scaleX);
                int h = (int) (CELL * scaleY);
                int offsetX = x + (CELL - w) / 2;
                int offsetY = y + (CELL - h) / 2;

                g.setColor(drawColor);
                g.fillRoundRect(offsetX, offsetY, w, h, 10, 10);

                Font tokenFont = fitFontToCell(g, baseFont, token, w, h);
                g.setFont(tokenFont);
                FontMetrics fm = g.getFontMetrics();

                int tw = fm.stringWidth(token);
                int th = fm.getAscent();
                int tx = offsetX + (w - tw) / 2;
                // ascent/descent을 고려해 중앙 정렬(뒤집기 얇은 구간에서도 글자 잘림 방지)
                int ty = offsetY + (h + th - fm.getDescent()) / 2;

                g.setColor(new Color(0, 0, 0, 110));
                g.drawString(token, tx + 1, ty + 1);
                g.setColor(Color.white);
                g.drawString(token, tx, ty);
                g.setFont(baseFont);

                g.setColor(GRID);
                g.drawRoundRect(x, y, CELL, CELL, 10, 10);

                if (anim != null && progress >= 1.0) {
                    animations.remove(pos);
                }
            }
        }
    }

    private Font fitFontToCell(Graphics2D g, Font base, String text, int cellW, int cellH) {
        int maxW = Math.max(10, cellW - 8);
        int maxH = Math.max(10, cellH - 4);
        Font f = base;
        FontMetrics fm = g.getFontMetrics(f);

        while ((fm.stringWidth(text) > maxW || fm.getHeight() > maxH) && f.getSize2D() > 9f) {
            f = f.deriveFont(f.getSize2D() - 1f);
            fm = g.getFontMetrics(f);
        }

        if (fm.stringWidth(text) > maxW) {
            String ellipsis = "...";
            for (int cut = text.length(); cut > 0; cut--) {
                String candidate = text.substring(0, cut) + ellipsis;
                if (fm.stringWidth(candidate) <= maxW) {
                    text = candidate;
                    break;
                }
            }
        }
        return f;
    }

    public void animateFlips(List<GameModel.FlipResult> flips) {
        long now = System.currentTimeMillis();
        for (GameModel.FlipResult f : flips) {
            animations.put(f.pos(), new FlipAnim(now, new Color(f.from().rgb), f.fromToken()));
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
        FlipAnim(long startMs, Color from, String fromToken) {
            this.startMs = startMs;
            this.from = from;
            this.fromToken = fromToken;
        }
        double progress() {
            double t = (System.currentTimeMillis() - startMs) / (double) ANIM_MS;
            return Math.min(1.0, t);
        }
    }
}
