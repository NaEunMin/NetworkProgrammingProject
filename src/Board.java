import java.io.Serializable;

/**
 * 게임 보드 (R x C 격자).
 * - 각 칸(r, c)에 Cell 객체를 저장한다.
 * - 네트워크 전송을 위해 Serializable 구현
 */
public class Board implements Serializable {
    // 직렬화 ID
    private static final long serialVersionUID = 2L;

    private final Cell[][] cells;
    private final int rows;
    private final int cols;

    public Board(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.cells = new Cell[rows][cols];
    }

    public int rows() { return rows; }
    public int cols() { return cols; }

    public Cell get(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return null;
        return cells[r][c];
    }

    public void set(int r, int c, Cell cell) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return;
        cells[r][c] = cell;
    }
}