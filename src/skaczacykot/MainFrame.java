package skaczacykot;

import javax.swing.*;

/**
 * @author baran
 */
public class MainFrame extends javax.swing.JFrame {

    // --- Zmienne GUI ---
    

    // --- Panel gry ---
    private GamePanel gamePanel;

    public MainFrame() {
        initComponents();

        setTitle("Barański Bartłomiej – Skaczący Kot");
        setLocationRelativeTo(null);
        setResizable(true);

        // Utwórz i dodaj GamePanel do CENTER
        gamePanel = new GamePanel(this::updateHud, this::onGameOver);
        getContentPane().add(gamePanel, java.awt.BorderLayout.CENTER);

        // Akcje przycisków
        btnPause.addActionListener(e -> gamePanel.togglePause());
        btnRestart.addActionListener(e -> gamePanel.restart());

        setSize(1100, 700); // wygodny start
        SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
    }

    /** Aktualizacja HUD (wołana przez GamePanel) */
    private void updateHud(int score, int coins) {
        lblScore.setText("Punkty: " + score + " | Monety: " + coins + "   (←/→ ruch, SPACJA skok, P pauza)");
    }

    /** Reakcja na koniec gry (wołana przez GamePanel) */
    private void onGameOver(int finalScore, int coins) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                "Przegrana!\nPunkty: " + finalScore + "\nMonety: " + coins,
                "Koniec gry",
                JOptionPane.INFORMATION_MESSAGE
        ));
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        topPanel = new javax.swing.JPanel();
        lblScore = new javax.swing.JLabel();
        btnPause = new javax.swing.JButton();
        btnRestart = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        lblScore.setText("Punkty: 0 | Monety: 0");
        topPanel.add(lblScore);

        btnPause.setText("Pauza (P)");
        topPanel.add(btnPause);

        btnRestart.setText("Restart");
        topPanel.add(btnRestart);

        getContentPane().add(topPanel, java.awt.BorderLayout.PAGE_START);

        pack();
    }// </editor-fold>//GEN-END:initComponents

     public static void main(String[] args) {
        // Nimbus (opcjonalnie)
        try {
            for (UIManager.LookAndFeelInfo info :
                    UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignore) {}

        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnPause;
    private javax.swing.JButton btnRestart;
    private javax.swing.JLabel lblScore;
    private javax.swing.JPanel topPanel;
    // End of variables declaration//GEN-END:variables
}
