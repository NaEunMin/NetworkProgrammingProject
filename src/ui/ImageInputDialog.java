package ui;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;

/**
 * 이미지 배경을 가진 커스텀 입력 다이얼로그
 * 
 * [설계 목적]
 * - 게임의 몰입감을 위해 기본 JOptionPane 대신 테마에 맞는 이미지를 배경으로 하는 입력창 제공
 * - 닉네임 입력 등 초기 진입 시 사용됨
 * 
 * [주요 기능]
 * - 이미지 로딩: 절대 경로, 상대 경로, 클래스패스 순으로 유연하게 이미지 검색
 * - 폴백(Fallback): 이미지 로드 실패 시 자동으로 기본 JOptionPane 사용
 * - 커스텀 UI: 윈도우 장식(TitleBar)을 제거하고 직접 구현하여 게임 분위기와 통일
 * - 스레드 안전성: invokeAndWait를 사용하여 어느 스레드에서 호출하든 EDT에서 실행 보장
 */
public class ImageInputDialog {

    /**
     * 다이얼로그 표시 및 입력값 반환
     * 
     * @param parent 부모 컴포넌트
     * @param message 표시할 메시지
     * @param initialValue 초기 입력값
     * @param imagePath 배경 이미지 경로
     * @param flip 이미지 좌우 반전 여부
     * @return 입력된 문자열 (취소 시 null)
     */
    public static String showInputDialog(Component parent, String message, String initialValue, String imagePath, boolean flip) {
        // 1. 이미지 로딩 시도 (여러 경로 탐색)
        BufferedImage loaded = null;
        try {
            File f = new File(imagePath);
            if (f.exists()) {
                System.out.println("ImageInputDialog: loading image from " + f.getAbsolutePath());
                loaded = ImageIO.read(f);
            } else {
                File f2 = new File(System.getProperty("user.dir"), imagePath);
                if (f2.exists()) {
                    System.out.println("ImageInputDialog: loading image from user.dir relative path: " + f2.getAbsolutePath());
                    loaded = ImageIO.read(f2);
                } else {
                    String classpathResource = imagePath.replace('\\', '/');
                    if (!classpathResource.startsWith("/")) classpathResource = "/" + classpathResource;
                    System.out.println("ImageInputDialog: trying classpath resource: " + classpathResource);
                    try (var is = ImageInputDialog.class.getResourceAsStream(classpathResource)) {
                        if (is != null) {
                            loaded = ImageIO.read(is);
                            System.out.println("ImageInputDialog: loaded image from classpath");
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ImageInputDialog: error while loading image: " + e.getMessage());
        }

        // 이미지가 없으면 기본 다이얼로그로 폴백
        if (loaded == null) {
            System.out.println("ImageInputDialog: no image found, falling back to JOptionPane");
            return JOptionPane.showInputDialog(parent, message, initialValue);
        }

        // 2. 이미지 전처리 (좌우 반전 등)
        BufferedImage imgToShow = loaded;
        if (flip) {
            try {
                AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
                tx.translate(-loaded.getWidth(), 0);
                AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_BILINEAR);
                BufferedImage flipped = op.filter(loaded, null);
                imgToShow = flipped;
            } catch (Exception ex) {
                System.err.println("ImageInputDialog: flip failed: " + ex.getMessage());
            }
        }

        final BufferedImage finalImg = imgToShow;
        final String[] result = new String[1]; // 결과값을 담을 배열 (람다 내부에서 접근 위해)

        try {
            // EDT에서 UI 생성 및 표시
            SwingUtilities.invokeAndWait(() -> {
                Window owner = (parent instanceof Component) ? SwingUtilities.getWindowAncestor((Component) parent) : null;
                final JDialog dlg = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
                dlg.setUndecorated(true); // 기본 윈도우 테두리 제거
                dlg.setResizable(false);

                // 커스텀 타이틀바 구현 (드래그 이동 지원)
                JPanel titleBar = new JPanel(new BorderLayout());
                titleBar.setBackground(new Color(240,240,240,230));
                titleBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200,200,200)));
                titleBar.setPreferredSize(new Dimension(100, 34));

                JLabel titleLabel = new JLabel("Input");
                titleLabel.setBorder(BorderFactory.createEmptyBorder(4,10,4,4));
                titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
                titleLabel.setForeground(new Color(80,80,80));
                titleBar.add(titleLabel, BorderLayout.WEST);

                JButton closeBtn = new JButton("X");
                closeBtn.setFocusable(false);
                closeBtn.setBorderPainted(false);
                closeBtn.setContentAreaFilled(false);
                closeBtn.setOpaque(false);
                closeBtn.setForeground(new Color(80,80,80));
                closeBtn.setPreferredSize(new Dimension(34, 34));
                titleBar.add(closeBtn, BorderLayout.EAST);

                // 타이틀바 드래그로 창 이동 구현
                final Point[] dragOffset = {new Point()};
                titleBar.addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        dragOffset[0] = e.getPoint();
                    }
                });
                titleBar.addMouseMotionListener(new MouseMotionAdapter() {
                    public void mouseDragged(MouseEvent e) {
                        Point p = dlg.getLocation();
                        dlg.setLocation(p.x + e.getX() - dragOffset[0].x, p.y + e.getY() - dragOffset[0].y);
                    }
                });

                // 이미지 패널 (배경 그리기)
                JPanel imagePanel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (finalImg != null) {
                            // 이미지 비율 유지하며 화면에 꽉 차게 그리기 (최대 크기 제한)
                            int imgW = finalImg.getWidth();
                            int imgH = finalImg.getHeight();
                            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                            int maxW = (int) (screen.width * 0.8);
                            int maxH = (int) (screen.height * 0.8);
                            double scale = Math.min(1.0, Math.min((double) maxW / imgW, (double) maxH / imgH));
                            int drawW = (int) (imgW * scale);
                            int drawH = (int) (imgH * scale);
                            int x = (getWidth() - drawW) / 2;
                            int y = (getHeight() - drawH) / 2;
                            g.drawImage(finalImg, x, y, drawW, drawH, null);
                        }
                    }
                };
                imagePanel.setLayout(new GridBagLayout());

                // 입력 패널 (반투명 배경)
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.anchor = GridBagConstraints.CENTER;
                gbc.insets = new Insets(4, 8, 4, 8);

                JPanel inputWrapper = new JPanel(new GridBagLayout());
                inputWrapper.setOpaque(true);
                inputWrapper.setBackground(new Color(255, 255, 255, 200)); // 반투명 흰색
                inputWrapper.setBorder(BorderFactory.createLineBorder(Color.GRAY));

                GridBagConstraints igbc = new GridBagConstraints();
                igbc.gridx = 0; igbc.gridy = 0; igbc.insets = new Insets(6,8,2,8);
                JLabel lbl = new JLabel(message);
                lbl.setForeground(Color.DARK_GRAY);
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
                inputWrapper.add(lbl, igbc);

                igbc.gridy = 1; igbc.insets = new Insets(2,8,6,8);
                JTextField tf = new JTextField(initialValue == null ? "" : initialValue, 20);
                tf.setPreferredSize(new Dimension(260, 24));
                inputWrapper.add(tf, igbc);

                igbc.gridy = 2; igbc.insets = new Insets(2,8,8,8);
                JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
                btns.setOpaque(false);
                JButton ok = new JButton("OK");
                JButton cancel = new JButton("Cancel");
                btns.add(ok);
                btns.add(cancel);
                inputWrapper.add(btns, igbc);

                imagePanel.add(inputWrapper, gbc);

                // 전체 컨테이너 구성
                JPanel container = new JPanel(new BorderLayout());
                container.add(titleBar, BorderLayout.NORTH);
                container.add(imagePanel, BorderLayout.CENTER);

                // 이벤트 핸들러
                closeBtn.addActionListener(a -> {
                    result[0] = null;
                    dlg.dispose();
                });

                ok.addActionListener(a -> {
                    result[0] = tf.getText();
                    dlg.dispose();
                });
                cancel.addActionListener(a -> {
                    result[0] = null;
                    dlg.dispose();
                });

                dlg.getContentPane().add(container);
                
                // 다이얼로그 크기 설정 (이미지 크기에 맞춤)
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                int maxW = (int) (screen.width * 0.8);
                int maxH = (int) (screen.height * 0.8);
                int imgW = finalImg.getWidth();
                int imgH = finalImg.getHeight();
                double scale = Math.min(1.0, Math.min((double) maxW / imgW, (double) maxH / imgH));
                int dialogW = Math.max(480, (int) (imgW * scale));
                int dialogH = Math.max(320, (int) (imgH * scale));
                dlg.setSize(dialogW, dialogH);
                dlg.setLocationRelativeTo(parent);
                
                // 창이 열리면 텍스트 필드에 포커스
                dlg.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        tf.requestFocusInWindow();
                    }
                });
                dlg.setVisible(true);
            });
        } catch (Exception e) {
            e.printStackTrace();
            return JOptionPane.showInputDialog(parent, message, initialValue);
        }

        return result[0];
    }
}
