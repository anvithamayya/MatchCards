import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.prefs.Preferences;
import javax.swing.*;

public class MatchCards {
    class Card {
        String cardName;
        ImageIcon cardImageIcon;

        Card(String cardName, ImageIcon cardImageIcon) {
            this.cardName = cardName;
            this.cardImageIcon = cardImageIcon;
        }
        public String toString() { return cardName; }
    }

    // --- Cards setup ---
    String[] cardList = {
        "darkness","double","fairy","fighting","fire",
        "grass","lightning","metal","psychic","water"
    };
    int rows = 4, columns = 5;
    int cardWidth = 100, cardHeight = 120;

    ArrayList<Card> cardSet;
    ImageIcon cardBackImageIcon;

    int boardWidth = columns * cardWidth;
    int boardHeight = rows * cardHeight;

    // --- UI ---
    JFrame frame = new JFrame("Pokemon Match Cards");
    JPanel textPanel = new JPanel();
    JLabel errorLabel = new JLabel();
    JLabel timerLabel = new JLabel();
    JLabel highScoreLabel = new JLabel();

    JPanel boardPanel = new JPanel();
    JPanel restartGamePanel = new JPanel();
    JButton restartButton = new JButton("Restart Game");

    // --- Game state ---
    ArrayList<JButton> board;
    boolean gameReady = false;
    JButton card1Selected, card2Selected;
    int errorCount = 0, matchedPairs = 0;

    // --- Timers ---
    Timer previewTimer;   // 3s: show all face-up at start/restart
    Timer mismatchTimer;  // 700ms: flip mismatched pair back
    Timer elapsedTimer;   // 1s: game timer
    int elapsedSeconds = 0;

    // --- High score (persisted) ---
    Preferences prefs = Preferences.userNodeForPackage(MatchCards.class);
    static final String PREF_BEST_ERRORS = "bestErrors";
    static final String PREF_BEST_TIME   = "bestTimeSeconds";
    int bestErrors = Integer.MAX_VALUE;
    int bestTimeSeconds = Integer.MAX_VALUE;

    public MatchCards() {
        loadHighScore();
        setupCards();
        shuffleCards();
        buildUI();
        wireTimers();
        startNewRound();
    }

    // ================= UI =================
    void buildUI() {
        frame.setLayout(new BorderLayout());
        frame.setSize(boardWidth, boardHeight);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Top bar: Errors | Time | Best
        textPanel.setLayout(new GridLayout(1,3));
        for (JLabel l : new JLabel[]{errorLabel, timerLabel, highScoreLabel}) {
            l.setFont(new Font("Arial", Font.PLAIN, 18));
            l.setHorizontalAlignment(SwingConstants.CENTER);
        }
        updateErrorLabel();
        updateTimerLabel();
        updateHighScoreLabel();
        textPanel.setPreferredSize(new Dimension(boardWidth, 36));
        textPanel.add(errorLabel);
        textPanel.add(timerLabel);
        textPanel.add(highScoreLabel);
        frame.add(textPanel, BorderLayout.NORTH);

        // Board
        board = new ArrayList<>();
        boardPanel.setLayout(new GridLayout(rows, columns));
        for (int i = 0; i < rows * columns; i++) {
            JButton tile = new JButton();
            tile.setPreferredSize(new Dimension(cardWidth, cardHeight));
            tile.setFocusable(false);
            tile.setOpaque(true);
            // (icon is set during preview/startNewRound)
            tile.addActionListener(this::onTileClick);
            board.add(tile);
            boardPanel.add(tile);
        }
        frame.add(boardPanel, BorderLayout.CENTER);

        // Restart
        restartButton.setFont(new Font("Arial", Font.PLAIN, 16));
        restartButton.setPreferredSize(new Dimension(boardWidth, 30));
        restartButton.setFocusable(false);
        restartButton.addActionListener(e -> startNewRound()); // <-- always allowed
        restartGamePanel.add(restartButton);
        frame.add(restartGamePanel, BorderLayout.SOUTH);

        frame.pack();
        frame.setVisible(true);
    }

    // ================ Timers ================
    void wireTimers() {
        // 3s preview: show all face-up, then flip all down and start the game
        previewTimer = new Timer(3000, e -> endPreviewStartGame());
        previewTimer.setRepeats(false);

        // 700ms mismatch flip
        mismatchTimer = new Timer(700, e -> flipMismatchedBack());
        mismatchTimer.setRepeats(false);

        // 1s elapsed timer
        elapsedTimer = new Timer(1000, e -> {
            elapsedSeconds++;
            updateTimerLabel();
        });
    }

    // ============ Game flow helpers ============
    void startNewRound() {
        // Stop any running timers
        if (elapsedTimer.isRunning()) elapsedTimer.stop();
        if (mismatchTimer.isRunning()) mismatchTimer.stop();
        if (previewTimer.isRunning()) previewTimer.stop();

        // Reset state
        gameReady = false;
        card1Selected = null;
        card2Selected = null;
        matchedPairs = 0;
        errorCount = 0;
        elapsedSeconds = 0;
        updateErrorLabel();
        updateTimerLabel();

        // Shuffle & show ALL face-up for preview
        shuffleCards();
        for (int i = 0; i < board.size(); i++) {
            board.get(i).setIcon(cardSet.get(i).cardImageIcon);
        }

        // Disable restart while preview is running (optional)
        restartButton.setEnabled(false);

        // Begin the 3s preview
        previewTimer.start();
    }

    void endPreviewStartGame() {
        // Flip all to back, enable clicks, start timer
        for (int i = 0; i < board.size(); i++) {
            board.get(i).setIcon(cardBackImageIcon);
        }
        gameReady = true;
        elapsedSeconds = 0;
        updateTimerLabel();
        elapsedTimer.start();
        restartButton.setEnabled(true); // allow restart during the round
    }

    void onTileClick(ActionEvent e) {
        if (!gameReady) return;                 // ignore during preview/end
        if (mismatchTimer.isRunning()) return;  // ignore while flipping wrong pair

        JButton tile = (JButton) e.getSource();
        if (tile.getIcon() != cardBackImageIcon) return; // already face-up or matched

        if (card1Selected == null) {
            card1Selected = tile;
            int i = board.indexOf(tile);
            tile.setIcon(cardSet.get(i).cardImageIcon);
            return;
        }

        if (card2Selected == null && tile != card1Selected) {
            card2Selected = tile;
            int i = board.indexOf(tile);
            tile.setIcon(cardSet.get(i).cardImageIcon);

            // Compare
            if (card1Selected.getIcon() != card2Selected.getIcon()) {
                errorCount++;
                updateErrorLabel();
                mismatchTimer.start();
            } else {
                matchedPairs++;
                card1Selected = null;
                card2Selected = null;

                // Check win
                if (matchedPairs == cardList.length) {
                    gameReady = false;
                    elapsedTimer.stop();
                    evaluateHighScore();
                    showResultMessage();
                    restartButton.setEnabled(true); // can restart immediately
                }
            }
        }
    }

    void flipMismatchedBack() {
        if (card1Selected != null) card1Selected.setIcon(cardBackImageIcon);
        if (card2Selected != null) card2Selected.setIcon(cardBackImageIcon);
        card1Selected = null;
        card2Selected = null;
    }

    // ============= Cards / Images =============
    void setupCards() {
        cardSet = new ArrayList<>();
        for (String cardName : cardList) {
            Image cardImg = new ImageIcon(getClass().getResource("./img/" + cardName + ".jpg")).getImage();
            ImageIcon icon = new ImageIcon(cardImg.getScaledInstance(cardWidth, cardHeight, Image.SCALE_SMOOTH));
            cardSet.add(new Card(cardName, icon));
        }
        // duplicate to make pairs
        cardSet.addAll(new ArrayList<>(cardSet));

        Image back = new ImageIcon(getClass().getResource("./img/back.jpg")).getImage();
        cardBackImageIcon = new ImageIcon(back.getScaledInstance(cardWidth, cardHeight, Image.SCALE_SMOOTH));
    }

    void shuffleCards() {
        for (int i = 0; i < cardSet.size(); i++) {
            int j = (int) (Math.random() * cardSet.size());
            Card tmp = cardSet.get(i);
            cardSet.set(i, cardSet.get(j));
            cardSet.set(j, tmp);
        }
    }

    // ============= Results / High score =============
    void showResultMessage() {
        String performance = (errorCount <= 5) ? "Excellent!"
                            : (errorCount <= 15) ? "Good!" : "Poor!";
        String timeStr = formatTime(elapsedSeconds);
        String bestStr = (bestErrors == Integer.MAX_VALUE) ? "—"
                        : (bestErrors + " errors, " + formatTime(bestTimeSeconds));

        JOptionPane.showMessageDialog(frame,
            "Game Over!\n"
          + "Errors: " + errorCount + "\n"
          + "Time: " + timeStr + "\n"
          + "Result: " + performance + "\n\n"
          + "Best: " + bestStr,
            "Match Result",
            JOptionPane.INFORMATION_MESSAGE
        );
    }

    void evaluateHighScore() {
        boolean isNewBest = false;
        if (errorCount < bestErrors) isNewBest = true;
        else if (errorCount == bestErrors && elapsedSeconds < bestTimeSeconds) isNewBest = true;

        if (isNewBest) {
            bestErrors = errorCount;
            bestTimeSeconds = elapsedSeconds;
            saveHighScore();
            updateHighScoreLabel();
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame,
                    "New High Score!\n"
                  + "Errors: " + bestErrors + "\n"
                  + "Time: " + formatTime(bestTimeSeconds),
                    "High Score",
                    JOptionPane.INFORMATION_MESSAGE)
            );
        }
    }

    void loadHighScore() {
        bestErrors = prefs.getInt(PREF_BEST_ERRORS, Integer.MAX_VALUE);
        bestTimeSeconds = prefs.getInt(PREF_BEST_TIME, Integer.MAX_VALUE);
    }

    void saveHighScore() {
        prefs.putInt(PREF_BEST_ERRORS, bestErrors);
        prefs.putInt(PREF_BEST_TIME, bestTimeSeconds);
    }

    // ============= UI label helpers =============
    void updateErrorLabel() { errorLabel.setText("Errors: " + errorCount); }
    void updateTimerLabel() { timerLabel.setText("Time: " + formatTime(elapsedSeconds)); }
    void updateHighScoreLabel() {
        if (bestErrors == Integer.MAX_VALUE) highScoreLabel.setText("Best: —");
        else highScoreLabel.setText("Best: " + bestErrors + " errors " + formatTime(bestTimeSeconds));
    }
    String formatTime(int totalSeconds) {
        int m = totalSeconds / 60, s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignore) {}
        new MatchCards();
    }
}