import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;

/**
 * 이미지 배경을 사용하는 간단한 입력 다이얼로그.
 * - 이미지가 없으면 자동으로 `JOptionPane`로 폴백합니다.
 * - `flip`을 true로 하면 이미지를 좌우 반전합니다.
 */
public class ImageInputDialog {

    public static String showInputDialog(Component parent, String message, String initialValue, String imagePath, boolean flip) {
        // Try loading image from several locations so dialog works regardless of working dir.
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

        if (loaded == null) {
            System.out.println("ImageInputDialog: no image found, falling back to JOptionPane");
            return JOptionPane.showInputDialog(parent, message, initialValue);
        }

        // Optionally flip horizontally
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
        final String[] result = new String[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                Window owner = (parent instanceof Component) ? SwingUtilities.getWindowAncestor((Component) parent) : null;
                final JDialog dlg = new JDialog(owner, Dialog.ModalityType.APPLICATION_MODAL);
                dlg.setUndecorated(true);
                dlg.setResizable(false);

                // Custom title bar (to mimic the screenshot: title + close X)
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

                // Drag support for title bar
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

                JPanel imagePanel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (finalImg != null) {
                            // scale image preserving aspect ratio and center it
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

                // input area: use a small semi-transparent panel centered over the image
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = 0;
                gbc.gridy = 0;
                gbc.anchor = GridBagConstraints.CENTER;
                gbc.insets = new Insets(4, 8, 4, 8);

                JPanel inputWrapper = new JPanel(new GridBagLayout());
                inputWrapper.setOpaque(true);
                inputWrapper.setBackground(new Color(255, 255, 255, 200));
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

                // container: put title bar on top and image panel center
                JPanel container = new JPanel(new BorderLayout());
                container.add(titleBar, BorderLayout.NORTH);
                container.add(imagePanel, BorderLayout.CENTER);

                // close button action
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
                // choose dialog size based on scaled image but ensure minimums
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
