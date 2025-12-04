package skaczacykot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class GamePanel extends JComponent implements ActionListener, KeyListener {

    // --- Config (świat i kamera) ---
    private static final int TARGET_FPS = 60;
    private static final double DT = 1.0 / TARGET_FPS;

    private static final int WORLD_W = 1920;   // szerokość świata (współrzędne rysowania)
    private static final int VIEW_H  = 1080;   // wysokość widoku (przestrzeń "kamery")
    private static final int START_FLOOR_Y = 520; // startowa "ziemia" pod graczem (wsp. świata)

    // Ruch/sterowanie
    private static final double GRAVITY    = 2000;
    private static final double MOVE_ACCEL = 3000;
    private static final double MOVE_MAX   = 450;
    private static final double JUMP_VY    = 1200; // Max skok to ok. 360 px

    // Scroll kamery (px/s) – lekko rośnie w czasie
    private static final double SCROLL_SPEED_BASE = 120;
    private static final double SCROLL_SPEED_GROW = 0.015; // przyrost prędkości na sekundę

    // Generowanie platform
    private static final int MIN_PLAT_W = 220;
    private static final int MAX_PLAT_W = 420;
    private static final int PLAT_H     = 40;
    private static final int STEP_HEIGHT = 300; // Maksymalny ZASIĘG skoku w pionie to ok. 360px. 300px jest bezpieczne.
    private static final int MAX_HORIZ_GAP = 500; // Maksymalny bezpieczny dystans poziomy (max zasięg to ok. 540px)
    private static final double COIN_PROB = 0.45; // szansa monety na platformie
    
    // ZWIĘKSZONA CZĘSTOTLIWOŚĆ KOLCÓW (20% szansy)
    private static final double SPIKE_PROB = 0.20; 

    // --- State ---
    private final javax.swing.Timer timer;

    private boolean paused = false;
    private boolean gameOver = false;

    // Player (współrzędne świata)
    private double px, py;
    private double vx, vy;
    private boolean onGround;

    // Kamera (przesuwa się w górę – zmniejsza camY)
    private double camY = 0;           // górny offset kamery względem świata (y świata - camY => y na ekranie)
    private double totalTime = 0;      // suma czasu (do przyrostu prędkości)

    // Animacje / grafiki
    private BufferedImage bg;
    private BufferedImage platformImg, spikeImg;
    private final BufferedImage[] catFrames  = new BufferedImage[4];
    private final BufferedImage[] coinFrames = new BufferedImage[8];
    private int catFrameId = 0;
    private int coinFrameId = 0;
    private double animTime = 0;

    // Obiekty świata
    private static class Plat { int x, y, w, h; Plat(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;} }
    private static class Coin { int x, y; Coin(int x,int y){this.x=x;this.y=y;} }

    private final List<Plat> platforms = new ArrayList<>();
    private final List<Point> spikes   = new ArrayList<>();
    private final List<Coin>  coins    = new ArrayList<>();

    // Sterowanie
    private boolean left, right;
    private boolean jumpKeyDown = false;
    private boolean jumpQueued  = false;

    // Wynik
    private int score = 0;
    private int coinsCollected = 0;

    // HUD callbacki
    public interface HudUpdater { void update(int score, int coins); }
    public interface GameOverListener { void onGameOver(int finalScore, int coins); }

    private final HudUpdater hudUpdater;
    private final GameOverListener gameOverListener;

    // Dźwięk
    private final SoundManager sound = new SoundManager();

    // RNG i generacja
    private final Random rnd = new Random(42);
    private int nextSpawnY;  // najbliższa Y (świata) do wygenerowania kolejnej platformy (idąc w górę zmniejszamy y)

    public GamePanel(HudUpdater hudUpdater, GameOverListener gameOverListener) {
        this.hudUpdater = hudUpdater;
        this.gameOverListener = gameOverListener;

        setFocusable(true);
        setOpaque(true);
        setBackground(new Color(230,230,230));
        setPreferredSize(new Dimension(1100, 700));

        addKeyListener(this);
        initAssets();
        initWorld();

        timer = new javax.swing.Timer((int) Math.round(1000.0 / TARGET_FPS), this);

        timer.start();

        sound.playBgLoop("bg.wav");
    }

    // --- Assets ---
    private void initAssets() {
        bg          = Assets.img("bg.png");
        platformImg = Assets.img("platform.png");
        spikeImg    = Assets.img("spike.png");
        
        
        for (int i = 0; i < catFrames.length; i++) {
            catFrames[i]  = Assets.img("cat_run_" + i + ".png");
            
        }
        
        for (int i = 0; i < coinFrames.length; i++) {
            coinFrames[i] = Assets.img("coin_" + i + ".png");
            
        }
        
    }

    // --- World init ---
    private void initWorld() {
        // Reset
        platforms.clear();
        spikes.clear();
        coins.clear();

        // Pozycja gracza – stoi na pierwszej platformie/ziemi
        px = WORLD_W / 2.0;
        py = START_FLOOR_Y - 32; // (środek gracza 32px nad górą platformy => stopy na platformie)
        vx = 0; vy = 0; onGround = true;

        // Kamera na dole
        camY = 0;
        totalTime = 0;

        // Płaska „ziemia” startowa
        platforms.add(new Plat(0, START_FLOOR_Y, WORLD_W, 80));

        // Wygeneruj kilka pierwszych platform w górę
        nextSpawnY = START_FLOOR_Y - STEP_HEIGHT;  
        while (nextSpawnY > -2*VIEW_H) { // generuj 2 ekrany w górę
            spawnPlatformAt(nextSpawnY);
            nextSpawnY -= STEP_HEIGHT;
        }

        // Wynik
        score = 0;
        coinsCollected = 0;
        hudUpdater.update(score, coinsCollected);
        gameOver = false;
        
        // RESETOWANIE STANU WEJŚCIA PO RESTARCIE - problem samoczynnego ruchu/skoku
        left = false;
        right = false;
        jumpKeyDown = false;
        jumpQueued = false;
    }

    private void spawnPlatformAt(int y) {
        int w = rndBetween(MIN_PLAT_W, MAX_PLAT_W);
        
        // --- Logika generowania X (ograniczenie poziomego zasięgu) ---
        int targetXMin = (int) px - MAX_HORIZ_GAP;
        int targetXMax = (int) px + MAX_HORIZ_GAP;
        int worldXMin = 80; 
        int worldXMax = WORLD_W - 80 - w; 
        
        int minX = Math.max(targetXMin, worldXMin);
        int maxX = Math.min(targetXMax, worldXMax);
        
        if (minX > maxX) {
            minX = worldXMin;
            maxX = worldXMax;
        }

        int x = rndBetween(minX, maxX);
        
        // ------------------------------------------------------------------------

        platforms.add(new Plat(x, y, w, PLAT_H));

        // Czasem moneta na platformie
        if (rnd.nextDouble() < COIN_PROB) {
            int cx = x + rndBetween(24, Math.max(24, w - 24 - 32));
            int cy = y - 40; // nad platformą
            coins.add(new Coin(cx, cy));
        }

        // --- Generowanie kolców z użyciem SPIKE_PROB ---
        if (spikeImg != null && w >= 128) { 
            if (y == START_FLOOR_Y) {
                // Kolce na ziemi startowej
                spikes.add(new Point(WORLD_W/2 - 32, START_FLOOR_Y - 48));
            } else if (rnd.nextDouble() < SPIKE_PROB) { // Wykorzystanie ZWIĘKSZONEJ szansy
                int spX = x + rndBetween(32, w - 64 - 32); 
                int spY = y - 48; 
                spikes.add(new Point(spX, spY));
            }
        }
        // ----------------------------------------------------
    }

    private int rndBetween(int a, int b) {
        return a + rnd.nextInt(Math.max(1, b - a + 1));
    }

    
    public void restart() {
        initWorld();
        paused = false;
        requestFocusInWindow();
        sound.stopBg();
        sound.playBgLoop("bg.wav");
    }
    public void togglePause() {
        paused = !paused;
        requestFocusInWindow();
    }

    // --- Pętla gry ---
    @Override public void actionPerformed(ActionEvent e) {
        if (paused || gameOver) { repaint(); return; }
        step(DT);
        repaint();
    }

    private void step(double dt) {
        totalTime += dt;
        animTime  += dt;
        if (animTime > 0.12) {
            animTime = 0;
            catFrameId  = (catFrameId  + 1) % catFrames.length;
            coinFrameId = (coinFrameId + 1) % coinFrames.length;
        }

        // Sterowanie poziome
        double ax = 0;
        if (left)  ax -= MOVE_ACCEL;
        if (right) ax += MOVE_ACCEL;

        vx += ax * dt;

        // Tłumienie bez wejścia
        if (!left && !right) {
            double fr = 2200 * dt;
            if (Math.abs(vx) <= fr) vx = 0; else vx -= Math.signum(vx) * fr;
        }

        // Ogranicz prędkość poziomą
        if (vx >  MOVE_MAX) vx =  MOVE_MAX;
        if (vx < -MOVE_MAX) vx = -MOVE_MAX;

        // Skok (edge trigger)
        if (jumpQueued && onGround) {
            vy = -JUMP_VY;
            onGround = false;
            jumpQueued = false;
            sound.playSfx("jump.wav");
        }

        // Grawitacja tylko w locie
        if (!onGround) vy += GRAVITY * dt; else vy = 0;

        // Integracja
        double newPx = px + vx * dt;
        double newPy = py + vy * dt;

        // Wrap poziomy
        if (newPx < -64)       newPx = WORLD_W + 64;
        if (newPx > WORLD_W + 64) newPx = -64;

        // Kolizje z platformami
        Rectangle playerNext = new Rectangle((int)newPx - 40, (int)newPy - 64, 80, 96);
        onGround = false;

        // kolizje tylko z platformami w pobliżu widoku
        int viewTop    = (int)camY - 200;
        int viewBottom = (int)camY + VIEW_H + 200;

        for (Plat plat : platforms) {
            if (plat.y > viewBottom || plat.y + plat.h < viewTop) continue;

            Rectangle r = new Rectangle(plat.x, plat.y, plat.w, plat.h);
            if (!playerNext.intersects(r)) continue;

            Rectangle playerCurr = new Rectangle((int)px - 40, (int)py - 64, 80, 96);

            // Lądowanie z góry
            if (playerCurr.y + playerCurr.height <= r.y && vy >= 0) {
                newPy = r.y - 32; // stopy na górze platformy
                vy = 0;
                onGround = true;
                playerNext.setLocation((int)newPx - 40, (int)newPy - 64);
            }
            // Odbicie od sufitu
            else if (playerCurr.y >= r.y + r.height && vy < 0) {
                newPy = r.y + r.height + 64;
                vy = 50;
                playerNext.setLocation((int)newPx - 40, (int)newPy - 64);
            }
            // Z boku
            else {
                if (vx > 0) newPx = r.x - (playerNext.width / 2.0);
                else        newPx = r.x + r.width + (playerNext.width / 2.0);
                vx = 0;
                playerNext.setLocation((int)newPx - 40, (int)newPy - 64);
            }
        }

        if (onGround) vy = 0;

        // Kolizje monet
        Rectangle playerHit = new Rectangle((int)newPx - 36, (int)newPy - 60, 72, 90);
        for (int i = coins.size()-1; i >= 0; i--) {
            Coin c = coins.get(i);
            if (c.y > viewBottom || c.y < viewTop) continue;
            if (playerHit.intersects(new Rectangle(c.x, c.y, 32, 32))) {
                coins.remove(i);
                coinsCollected++;
                score += 20;
                hudUpdater.update(score, coinsCollected);
                sound.playSfx("coin.wav");
            }
        }

        // Kolizja z kolcami (Game Over)
        Rectangle playerDanger = new Rectangle((int)px - 32, (int)py - 56, 64, 88); 
        for (Point sp : spikes) {
            Rectangle spikeHit = new Rectangle(sp.x + 8, sp.y + 16, 48, 32); 
            if (playerDanger.intersects(spikeHit)) {
                gameOver();
                return;
            }
        }

        // Zatwierdź pozycję gracza
        px = newPx; py = newPy;

        // Auto–scroll kamery w górę (zmniejszamy Y, bo góra to ujemne wartości)
        double scrollSpeed = SCROLL_SPEED_BASE + SCROLL_SPEED_GROW * (totalTime * 100.0);
        camY -= scrollSpeed * dt;

        // Jeśli gracz spadnie poniżej dołu ekranu -> Game Over
        double screenY = py - camY; 
        if (screenY > VIEW_H + 120) {
            gameOver();
            return;
        }

        // Generowanie platform wyżej, jeśli zbliżamy się do góry widoku
        while (nextSpawnY > camY - VIEW_H) {
            spawnPlatformAt(nextSpawnY);
            nextSpawnY -= STEP_HEIGHT;
        }

        // Sprzątanie obiektów daleko poniżej widoku
        int killY = (int)camY + VIEW_H + 400;
        platforms.removeIf(p -> p.y > killY);
        coins.removeIf(c -> c.y > killY);
        spikes.removeIf(sp -> sp.y > killY); 

        // Punkty rosną z czasem/przesuwem
        score += 1;
        if (score % 30 == 0) hudUpdater.update(score, coinsCollected);
    }

    private void gameOver() {
        gameOver = true;
        sound.playSfx("hit.wav");
        sound.stopBg();
        if (gameOverListener != null) gameOverListener.onGameOver(score, coinsCollected);
    }

    // --- Render ---
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        // 1. ZAPISZ oryginalną transformację
        AffineTransform systemTransform = g2.getTransform();

        int w = getWidth(), h = getHeight();

        // Skala rysowania gry (świat 1920x1080 -> panel)
        double sx = w / (double) WORLD_W;
        double sy = h / (double) VIEW_H;
        g2.scale(sx, sy);

        // --- RYSOWANIE GRY (Tło, platformy, kot) ---
        
        // Tło
        if (bg != null) {
            int bgH = VIEW_H;
            int offset = (int)((Math.abs(camY)) % bgH); 
            g2.drawImage(bg, 0, offset - bgH, WORLD_W, offset, 0, 0, bg.getWidth(), bg.getHeight(), null);
            g2.drawImage(bg, 0, offset, WORLD_W, offset + bgH, 0, 0, bg.getWidth(), bg.getHeight(), null);
        } else {
            g2.setPaint(new GradientPaint(0, (float)camY, new Color(210,230,255),
                                          0, (float)(camY+VIEW_H), new Color(150,180,255)));
            g2.fillRect(0, (int)camY, WORLD_W, VIEW_H);
        }

        g2.translate(0, -camY); // Kamera

        // Platformy
        for (Plat p : platforms) {
            if (platformImg != null) {
                for (int x = p.x; x < p.x + p.w; x += platformImg.getWidth()) {
                    int dw = Math.min(platformImg.getWidth(), p.x + p.w - x);
                    g2.drawImage(platformImg, x, p.y, x+dw, p.y + p.h, 0, 0, dw, platformImg.getHeight(), null);
                }
            } else {
                g2.setColor(new Color(70,140,70));
                g2.fillRect(p.x, p.y, p.w, p.h);
            }
        }

        // Monety
        BufferedImage coin = coinFrames[coinFrameId];
        for (Coin c : coins) {
            if (coin != null) g2.drawImage(coin, c.x, c.y, 32, 32, null);
            else { g2.setColor(Color.YELLOW); g2.fillOval(c.x, c.y, 32, 32); }
        }

        // Kolce
        for (Point sp : spikes) {
            if (spikeImg != null) g2.drawImage(spikeImg, sp.x, sp.y, 64, 64, null);
            else {
                g2.setColor(Color.RED);
                g2.fillPolygon(new int[]{sp.x, sp.x+32, sp.x+64}, new int[]{sp.y+64, sp.y, sp.y+64}, 3);
            }
        }

        // Kot
        BufferedImage cat = catFrames[catFrameId];
        int drawX = (int)px - 64;
        int drawY = (int)py - 96;
        if (cat != null) {
            if (vx >= 0) g2.drawImage(cat, drawX, drawY, 128, 128, null);
            else         g2.drawImage(cat, drawX+128, drawY, -128, 128, null);
        } else {
            g2.setColor(Color.BLACK);
            g2.fillRect(drawX, drawY, 80, 96);
        }

        // --- UI / OVERLAY ---
        
        
        g2.setTransform(systemTransform); 
        
        if (paused) {
            drawOverlayText(g2, w, h, "PAUZA");
        } else if (gameOver) {
            drawOverlayText(g2, w, h, "KONIEC GRY");
        }

        g2.dispose();
    }

    private void drawOverlayText(Graphics2D g2, int w, int h, String text) {
        g2.setColor(new Color(0,0,0,150));
        g2.fillRect(0,0,w,h);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (w - fm.stringWidth(text))/2;
        int ty = (h - fm.getHeight())/2 + fm.getAscent();
        g2.setColor(Color.WHITE);
        g2.drawString(text, tx, ty);
    }

    // --- Input ---
    @Override public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> left = true;
            case KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_SPACE -> {
                if (!jumpKeyDown) { jumpKeyDown = true; jumpQueued = true; }
            }
            case KeyEvent.VK_P     -> togglePause();
        }
    }
    @Override public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT  -> left = false;
            case KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_SPACE -> jumpKeyDown = false;
        }
    }
    @Override public void keyTyped(KeyEvent e) { /* not used */ }
}