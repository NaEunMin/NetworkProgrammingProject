import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

public class BonusGamePanel extends JPanel {

    private enum State {
        IDLE, SHIP_ENTERING, CHAINS_DROPPING, ACTIVE, EXITING
    }

    private State state = State.IDLE;
    private final Timer animTimer;
    
    // Assets
    private BufferedImage shipImage;
    private BufferedImage chainImage;
    
    // Animation Variables
    private double shipX; // Ship's current X position
    private double targetShipX; // Center position
    private double[] chainY; // Current Y position for each chain
    private double targetChainY; // Target Y position for chains
    
    private List<String> sentences = new ArrayList<>();
    private boolean[] solved; // Track solved sentences
    
    private static final int SHIP_WIDTH = 500; // [MODIFIED] Slightly larger
    private static final int SHIP_HEIGHT = 350;
    private static final int CHAIN_WIDTH = 30;
    
    public BonusGamePanel() {
        setOpaque(false);
        loadAssets();
        
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
    
    // [NEW] Particle System for Explosion
    private static class Particle {
        double x, y;
        double vx, vy;
        Color color;
        float life; // 1.0 -> 0.0

        Particle(double x, double y) {
            this.x = x;
            this.y = y;
            double angle = Math.random() * Math.PI * 2;
            double speed = 2 + Math.random() * 5;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
            this.life = 1.0f;
            
            // Fire colors
            int r = 200 + (int)(Math.random() * 55);
            int g = 100 + (int)(Math.random() * 100);
            int b = 0;
            this.color = new Color(r, g, b);
        }
    }

    private List<Particle> particles = new ArrayList<>();
    private double[] chainVelocity; // Falling speed for each chain
    private boolean[] isFalling;    // Is the chain broken?

    public void startBonusTime(List<String> sentences) {
        this.sentences = new ArrayList<>(sentences);
        this.solved = new boolean[sentences.size()];
        this.state = State.SHIP_ENTERING;
        
        // Initial positions
        this.shipX = -SHIP_WIDTH; // [MODIFIED] Start from left outside
        this.targetShipX = (getWidth() - SHIP_WIDTH) / 2.0;
        
        this.chainY = new double[sentences.size()];
        this.chainVelocity = new double[sentences.size()];
        this.isFalling = new boolean[sentences.size()];
        
        for(int i=0; i<chainY.length; i++) {
            chainY[i] = 10 + SHIP_HEIGHT / 2; // Start from ship center (where chains attach)
            chainVelocity[i] = 0;
            isFalling[i] = false;
        }
        
        // [MODIFIED] Stagger target Y positions to prevent overlap
        // Base target Y is below the ship (Y=10 + HEIGHT=350 -> 360)
        this.targetChainY = 10 + SHIP_HEIGHT + 20;
        
        animTimer.start();
        repaint();
    }
    
    public void solveSentence(String sentence) {
        for (int i = 0; i < sentences.size(); i++) {
            if (!solved[i] && sentences.get(i).equals(sentence)) {
                solved[i] = true;
                
                // [NEW] Trigger explosion and falling
                isFalling[i] = true;
                
                // Calculate explosion point (top of the text box / end of chain)
                int startX = (int)shipX + 50;
                int gap = (SHIP_WIDTH - 100) / Math.max(1, sentences.size());
                int x = startX + i * gap;
                int y = (int)chainY[i]; // Current Y of the text box
                
                // Explode at the connection point (a bit above the text box)
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
        // Ship moves out to left
    }
    
    private void updateAnimation() {
        // Update Particles
        for(int i=0; i<particles.size(); i++) {
            Particle p = particles.get(i);
            p.x += p.vx;
            p.y += p.vy;
            p.vy += 0.2; // Gravity for particles
            p.life -= 0.03f;
            if(p.life <= 0) {
                particles.remove(i);
                i--;
            }
        }

        if (state == State.SHIP_ENTERING) {
            // Ease out
            double diff = targetShipX - shipX;
            shipX += diff * 0.05;
            
            if (Math.abs(diff) < 1.0) {
                shipX = targetShipX;
                state = State.CHAINS_DROPPING;
            }
        } else if (state == State.CHAINS_DROPPING) {
            boolean allDown = true;
            for (int i = 0; i < chainY.length; i++) {
                // [MODIFIED] Calculate unique staggered target for each chain
                // Ensure all 5 chains have different lengths to prevent overlap
                // Increased spacing: 0, 90, 180, 40, 130
                double myTarget = targetChainY + ((i * 90) % 250);
                
                double diff = myTarget - chainY[i];
                chainY[i] += diff * 0.1; // Drop faster
                if (Math.abs(diff) > 1.0) allDown = false;
            }
            if (allDown) {
                state = State.ACTIVE;
            }
        } else if (state == State.ACTIVE) {
            // [NEW] Handle falling chains
            for(int i=0; i<chainY.length; i++) {
                if(isFalling[i]) {
                    chainVelocity[i] += 1.0; // Gravity
                    chainY[i] += chainVelocity[i];
                }
            }
        } else if (state == State.EXITING) {
            shipX += 10; // [MODIFIED] Move right to exit
            if (shipX > getWidth()) { // [MODIFIED] Check right boundary
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
        
        // [NEW] Draw background overlay to hide the grid, BUT exclude the input area
        g2.setColor(new Color(0, 0, 0, 200)); // Dark semi-transparent background
        
        if (inputArea != null && inputArea.isShowing()) {
            // Create an Area for the whole screen
            java.awt.geom.Area overlayArea = new java.awt.geom.Area(new Rectangle(0, 0, getWidth(), getHeight()));
            
            // Calculate inputArea bounds relative to this panel
            Point pt = SwingUtilities.convertPoint(inputArea, 0, 0, this);
            Rectangle inputRect = new Rectangle(pt.x, pt.y, inputArea.getWidth(), inputArea.getHeight());
            
            // Subtract the input area
            overlayArea.subtract(new java.awt.geom.Area(inputRect));
            
            // Fill the resulting area
            g2.fill(overlayArea);
        } else {
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        
        // Draw Ship
        if (shipImage != null) {
            g2.drawImage(shipImage, (int)shipX, 10, SHIP_WIDTH, SHIP_HEIGHT, null); // [MODIFIED] Y=10
        } else {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect((int)shipX, 10, SHIP_WIDTH, SHIP_HEIGHT);
        }
        
        // Draw Chains and Sentences
        if (state != State.SHIP_ENTERING) {
            int startX = (int)shipX + 50;
            int gap = (SHIP_WIDTH - 100) / Math.max(1, sentences.size());
            
            for (int i = 0; i < sentences.size(); i++) {
                // [MODIFIED] Draw falling items too, but detached
                if (solved[i] && !isFalling[i]) continue; // Should not happen if logic is correct
                
                int x = startX + i * gap;
                int y = (int)chainY[i];
                
                // Draw Chain (Only if NOT falling)
                if (!isFalling[i]) {
                    if (chainImage != null) {
                        g2.drawImage(chainImage, x - CHAIN_WIDTH/2, 10 + SHIP_HEIGHT/2, CHAIN_WIDTH, y - (10 + SHIP_HEIGHT/2), null);
                    } else {
                        g2.setColor(Color.GRAY);
                        g2.setStroke(new BasicStroke(3));
                        g2.drawLine(x, 10 + SHIP_HEIGHT/2, x, y);
                    }
                } else {
                    // [NEW] Draw broken chain fragment on top of the box
                     if (chainImage != null) {
                        // Draw a small piece attached to the box
                        g2.drawImage(chainImage, x - CHAIN_WIDTH/2, y - 30, CHAIN_WIDTH, 30, null);
                    }
                }
                
                // Draw Text Box
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
        
        // [NEW] Draw Particles
        for(Particle p : particles) {
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(p.life * 255)));
            g2.fillOval((int)p.x, (int)p.y, 6, 6);
        }
    }
}
