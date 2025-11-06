import javax.swing.*;
import java.awt.*;

/**
 * 중앙 보드 시각화 패널.
 * - 사진처럼 각 셀에 배경(팀 색), 가운데 토큰 텍스트, 얇은 테두리(격자)를 그린다.
 * - 셀 크기/폰트 크기를 상수로 두어 사진 비율에 가깝게 조정.
 */
public class BoardPanel extends JPanel {

    private static final int CELL   = 56; // 사진 비율에 맞춘 셀 한 변 픽셀
    private static final int PAD    = 14; // 보드 외곽 여백
    private static final Color GRID = new Color(0,0,0,40); // 은은한 테두리

    private final GameModel model;

    public BoardPanel(GameModel model) {
        this.model = model;
        setBackground(new Color(19, 36, 49)); // 바다색 배경 톤 근사
        setOpaque(true);
    }

    @Override
    public Dimension getPreferredSize() {
        Board b = model.board();
        return new Dimension(PAD*2 + b.cols()*CELL, PAD*2 + b.rows()*CELL);
    }

    @Override
    protected void paintComponent(Graphics raw) {
        super.paintComponent(raw);
        Graphics2D g = (Graphics2D) raw;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Board board = model.board();

        // 폰트 — 셀 크기 기준으로 자동 스케일
        float fontSize = Math.max(12f, CELL * 0.28f);
        g.setFont(getFont().deriveFont(Font.BOLD, fontSize));

        for (int r = 0; r < board.rows(); r++) {
            for (int c = 0; c < board.cols(); c++) {
                int x = PAD + c * CELL;
                int y = PAD + r * CELL;

                Cell cell = board.get(r, c);
                // 1) 배경(팀 색)
                g.setColor(new Color(cell.owner().rgb));
                g.fillRoundRect(x, y, CELL, CELL, 10, 10);

                // 2) 토큰 텍스트 — 중앙 정렬(얇은 외곽선으로 가독성 ↑)
                String token = cell.token();
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(token);
                int th = fm.getAscent();
                int tx = x + (CELL - tw)/2;
                int ty = y + (CELL + th)/2 - 4;

                g.setColor(new Color(0,0,0,110));
                g.drawString(token, tx+1, ty+1);
                g.setColor(Color.white);
                g.drawString(token, tx, ty);

                // 3) 격자(테두리)
                g.setColor(GRID);
                g.drawRoundRect(x, y, CELL, CELL, 10, 10);
            }
        }
    }
}
