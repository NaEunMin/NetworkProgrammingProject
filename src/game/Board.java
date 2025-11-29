package game;
import java.io.Serializable;

/**
 * 게임 보드 (R x C 격자)
 * 
 * [설계]
 * - 2차원 배열로 격자를 표현 (직관적이고 O(1) 접근 가능)
 * - 각 칸은 Cell 객체로 표현 (소유권과 토큰 문자 저장)
 * - 네트워크 전송을 위해 Serializable 구현 (게임 시작 시 서버→클라이언트로 전송)
 */
public class Board implements Serializable {
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