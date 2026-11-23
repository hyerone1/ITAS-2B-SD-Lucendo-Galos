import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class HuddleClient extends JFrame {
    static final Preferences PREFS = Preferences.userNodeForPackage(HuddleClient.class);
    static boolean dark = loadDark();
    static Color NAVY, PRIMARY, PRIMARY_DARK, PRIMARY_BG, PRIMARY_FG, TEAL, BG, CARD, FIELD, FIELD_OFF,
            TEXT, MUTED, HINT, BORDER, GREEN, GREEN_BG, RED;
    static Color[] NAME_COLORS;
    static final Color HEADER_MUTED = new Color(199, 210, 254);
    static final Color STATUS_OFF_BG = new Color(55, 48, 163);
    static final Color STATUS_ON_BG = new Color(220, 252, 231);
    static { applyPalette(dark); }

    static boolean loadDark() {
        try { return PREFS.getBoolean("dark", false); } catch (Exception e) { return false; }
    }

    static void applyPalette(boolean d) {
        dark = d;
        if (d) {
            NAVY = new Color(15, 14, 36);
            PRIMARY = new Color(99, 102, 241);
            PRIMARY_DARK = new Color(199, 210, 254);
            PRIMARY_BG = new Color(49, 46, 129);
            PRIMARY_FG = new Color(165, 180, 252);
            TEAL = new Color(13, 148, 136);
            BG = new Color(17, 19, 27);
            CARD = new Color(28, 31, 43);
            FIELD = new Color(18, 20, 29);
            FIELD_OFF = new Color(36, 39, 52);
            TEXT = new Color(229, 231, 240);
            MUTED = new Color(148, 155, 175);
            HINT = new Color(107, 114, 128);
            BORDER = new Color(55, 60, 78);
            GREEN = new Color(74, 222, 128);
            GREEN_BG = new Color(20, 83, 45);
            RED = new Color(239, 68, 68);
            NAME_COLORS = new Color[]{
                new Color(248, 113, 113), new Color(251, 191, 36), new Color(52, 211, 153), new Color(56, 189, 248),
                new Color(167, 139, 250), new Color(244, 114, 182), new Color(45, 212, 191), new Color(163, 230, 53)
            };
        } else {
            NAVY = new Color(30, 27, 75);
            PRIMARY = new Color(79, 70, 229);
            PRIMARY_DARK = new Color(55, 48, 163);
            PRIMARY_BG = new Color(238, 242, 255);
            PRIMARY_FG = new Color(79, 70, 229);
            TEAL = new Color(13, 148, 136);
            BG = new Color(243, 244, 246);
            CARD = Color.WHITE;
            FIELD = Color.WHITE;
            FIELD_OFF = new Color(243, 244, 246);
            TEXT = new Color(17, 24, 39);
            MUTED = new Color(107, 114, 128);
            HINT = new Color(156, 163, 175);
            BORDER = new Color(229, 231, 235);
            GREEN = new Color(22, 163, 74);
            GREEN_BG = new Color(220, 252, 231);
            RED = new Color(220, 38, 38);
            NAME_COLORS = new Color[]{
                new Color(220, 38, 38), new Color(217, 119, 6), new Color(5, 150, 105), new Color(2, 132, 199),
                new Color(124, 58, 237), new Color(219, 39, 119), new Color(13, 148, 136), new Color(101, 163, 13)
            };
        }
    }

    static void applyUiDefaults() {
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("Panel.background", CARD);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("Button.background", FIELD_OFF);
        UIManager.put("Button.foreground", TEXT);
    }

    static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }
    static String themed(String html) { return html.replace("#6b7280", hex(MUTED)).replace("#16a34a", hex(GREEN)); }
    static Color shade(Color c, int steps) {
        for (int i = 0; i < steps; i++) c = dark ? c.brighter() : c.darker();
        return c;
    }

    static final int MAX_MSG = 500;

    static final String FONT = pickFont();
    static String pickFont() {
        Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "Helvetica Neue", "Inter", "Roboto", "Ubuntu"})
            if (names.contains(f)) return f;
        return Font.SANS_SERIF;
    }
    static Font font(int style, int size) { return new Font(FONT, style, size); }
    static Border pad(int t, int l, int b, int r) { return BorderFactory.createEmptyBorder(t, l, b, r); }
    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    static Color colorFor(String name) { return NAME_COLORS[(name.hashCode() & 0x7fffffff) % NAME_COLORS.length]; }

    static class RoundButton extends JButton {
        private Color base;
        private boolean over;
        RoundButton(String text, Color base, Color fg) {
            super(text);
            this.base = base;
            setForeground(fg);
            setFont(font(Font.BOLD, 12));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(pad(8, 14, 8, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { over = true; repaint(); }
                public void mouseExited(MouseEvent e) { over = false; repaint(); }
            });
        }
        void setColors(Color base, Color fg) { this.base = base; setForeground(fg); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? BORDER
                    : getModel().isPressed() ? shade(base, 2)
                    : over ? shade(base, 1) : base;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class HintField extends JTextField {
        private final String hint;
        HintField(String hint, int cols) { super(cols); this.hint = hint; }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(HINT);
                g2.setFont(getFont());
                Insets in = getInsets();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    static class QItem { int id, votes; boolean done; String asker, text; }

    private final JTextField hostF = new HintField("Server IP", 11);
    private final JTextField portF = new HintField("Port", 5);
    private final JTextField nickF = new HintField("Nickname", 10);
    private final RoundButton connectB = new RoundButton("Connect", TEAL, Color.WHITE);
    private final JLabel statusLabel = new JLabel();
    private final JTextPane chat = new JTextPane();
    private final StyledDocument doc = chat.getStyledDocument();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JLabel peopleCount = new JLabel("0");
    private final JTextField input = new HintField("Type a message  (Enter to send, /help for commands)", 20);
    private final RoundButton sendB = new RoundButton("Send", PRIMARY, Color.WHITE);

    private final Map<Integer, QItem> qMap = new HashMap<>();
    private final DefaultListModel<QItem> qModel = new DefaultListModel<>();
    private final JList<QItem> qList = new JList<>(qModel);
    private final JTextField qField = new HintField("Ask the group a question...", 12);
    private final RoundButton qAdd = new RoundButton("Ask", PRIMARY, Color.WHITE);
    private final RoundButton qVote = new RoundButton("Upvote", PRIMARY_BG, PRIMARY_DARK);
    private final RoundButton qDone = new RoundButton("Answered", PRIMARY_BG, PRIMARY_DARK);
    private final RoundButton qPoll = new RoundButton("Poll it", PRIMARY_BG, PRIMARY_DARK);

    private final JLabel pollLabel = new JLabel();
    private final JPanel optPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final JTextField pollQ = new HintField("e.g. Which topic should we review first?", 12);
    private final JTextField pollOpts = new HintField("e.g. Chapter 1; Chapter 2; Chapter 3", 12);
    private final RoundButton pollStart = new RoundButton("Start poll", PRIMARY, Color.WHITE);
    private final RoundButton pollEnd = new RoundButton("End poll", RED, Color.WHITE);

    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private final RoundButton tabQ = new RoundButton("Question queue", PRIMARY, Color.WHITE);
    private final RoundButton tabP = new RoundButton("Live poll", PRIMARY_BG, PRIMARY_DARK);

    private final RoundButton themeB = new RoundButton("", new Color(255, 255, 255, 30), Color.WHITE);
    private final List<Runnable> themers = new ArrayList<>();
    private JPanel rootPanel;
    private boolean tabQueue = true;
    private String[] lastPoll = {"0", "", "", "", "-1"};
    private static class ChatLine { int kind; boolean me; String time, sender, text; }
    private final List<ChatLine> history = new ArrayList<>();
    private JList<String> userList;

    private SimpleAttributeSet S_TIME = style(MUTED, 11, false, false);
    private SimpleAttributeSet S_BODY = style(TEXT, 14, false, false);
    private SimpleAttributeSet S_SYS = style(MUTED, 12, false, true);
    private SimpleAttributeSet S_ERR = style(RED, 12, true, false);

    private volatile Socket sock;
    private volatile PrintWriter out;
    private volatile boolean connected, welcomed, leaving;
    private final ExecutorService networkWriter = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "net-writer");
        thread.setDaemon(true);
        return thread;
    });
    private int pollState;
    private int myVote = -1;
    private String pollKey = "";
    private String myNick = "";

    static SimpleAttributeSet style(Color c, int size, boolean bold, boolean italic) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, FONT);
        StyleConstants.setFontSize(a, size);
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, bold);
        StyleConstants.setItalic(a, italic);
        return a;
    }

    public HuddleClient() {
        super("HuddleUp - Study Circle Chat");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 660));
        setSize(1160, 760);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        rootPanel = root;
        root.add(buildHeader(), BorderLayout.NORTH);
        JPanel workspace = buildWorkspace();
        workspace.setBorder(pad(16, 20, 20, 20));
        root.add(workspace, BorderLayout.CENTER);
        setContentPane(root);

        hostF.setText("localhost");
        portF.setText("5000");
        styleControls();
        wireSend();
        themeB.addActionListener(e -> toggleTheme());
        connectB.addActionListener(e -> { if (connected) disconnect(); else connect(); });
        for (JTextField f : new JTextField[]{hostF, portF, nickF}) f.addActionListener(e -> connectB.doClick());
        pollStart.addActionListener(e -> startPoll());
        showTab(true);
        renderPoll(new String[]{"0", "", "", "", "-1"});
        setUi(false);
        applyTheme();

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                PrintWriter t = out;
                if (connected && t != null) t.println("MSG|/exit");
            }
        });
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        themers.add(() -> header.setBackground(NAVY));
        header.setBorder(pad(14, 20, 14, 20));

        JPanel brand = new JPanel(new BorderLayout(0, 2));
        brand.setOpaque(false);
        JLabel title = new JLabel("HuddleUp");
        title.setFont(font(Font.BOLD, 26));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("Study together. Ask smarter.");
        subtitle.setFont(font(Font.PLAIN, 12));
        subtitle.setForeground(HEADER_MUTED);
        brand.add(title, BorderLayout.NORTH);
        brand.add(subtitle, BorderLayout.SOUTH);

        hostF.setPreferredSize(new Dimension(150, 34));
        portF.setPreferredSize(new Dimension(70, 34));
        nickF.setPreferredSize(new Dimension(130, 34));
        connectB.setPreferredSize(new Dimension(110, 34));
        themeB.setPreferredSize(new Dimension(110, 34));

        JPanel conn = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        conn.setOpaque(false);
        conn.add(labeled("Server address", hostF));
        conn.add(labeled("Port", portF));
        conn.add(labeled("Your nickname", nickF));
        conn.add(labeled(" ", connectB));
        conn.add(labeled(" ", statusLabel));
        conn.add(labeled(" ", themeB));

        header.add(brand, BorderLayout.WEST);
        header.add(conn, BorderLayout.EAST);
        return header;
    }

    private JPanel labeled(String caption, JComponent comp) {
        JPanel g = new JPanel(new BorderLayout(0, 3));
        g.setOpaque(false);
        JLabel l = new JLabel(caption);
        l.setFont(font(Font.PLAIN, 11));
        l.setForeground(HEADER_MUTED);
        g.add(l, BorderLayout.NORTH);
        g.add(comp, BorderLayout.CENTER);
        return g;
    }

    private JScrollPane scroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        themers.add(() -> {
            sp.setBorder(BorderFactory.createLineBorder(BORDER));
            sp.getViewport().setBackground(CARD);
            sp.setBackground(CARD);
        });
        return sp;
    }

    private JPanel card(LayoutManager lm) {
        JPanel p = new JPanel(lm);
        themers.add(() -> {
            p.setBackground(CARD);
            p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), pad(14, 14, 14, 14)));
        });
        return p;
    }

    private JPanel buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(16, 0));
        workspace.setOpaque(false);

        JPanel conversation = card(new BorderLayout(0, 10));
        JPanel heading = new JPanel(new BorderLayout(0, 2));
        heading.setOpaque(false);
        JLabel chatTitle = new JLabel("Group chat");
        chatTitle.setFont(font(Font.BOLD, 17));
        themers.add(() -> chatTitle.setForeground(TEXT));
        JLabel hint = new JLabel("Everyone in the huddle sees your messages  \u00B7  type /help for commands");
        hint.setFont(font(Font.PLAIN, 12));
        themers.add(() -> hint.setForeground(MUTED));
        heading.add(chatTitle, BorderLayout.NORTH);
        heading.add(hint, BorderLayout.SOUTH);

        chat.setEditable(false);
        themers.add(() -> chat.setBackground(CARD));
        chat.setBorder(pad(10, 12, 10, 12));
        JScrollPane chatScroll = scroll(chat);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setOpaque(false);
        input.setPreferredSize(new Dimension(100, 42));
        sendB.setPreferredSize(new Dimension(90, 42));
        composer.add(input, BorderLayout.CENTER);
        composer.add(sendB, BorderLayout.EAST);

        conversation.add(heading, BorderLayout.NORTH);
        conversation.add(chatScroll, BorderLayout.CENTER);
        conversation.add(composer, BorderLayout.SOUTH);

        JPanel side = new JPanel(new BorderLayout(0, 14));
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(390, 0));

        JPanel people = card(new BorderLayout(0, 8));
        JPanel pHead = new JPanel(new BorderLayout());
        pHead.setOpaque(false);
        JLabel peopleTitle = new JLabel("People online");
        peopleTitle.setFont(font(Font.BOLD, 15));
        themers.add(() -> peopleTitle.setForeground(TEXT));
        peopleCount.setFont(font(Font.BOLD, 12));
        peopleCount.setOpaque(true);
        themers.add(() -> { peopleCount.setForeground(PRIMARY_DARK); peopleCount.setBackground(PRIMARY_BG); });
        peopleCount.setBorder(pad(2, 9, 2, 9));
        pHead.add(peopleTitle, BorderLayout.WEST);
        pHead.add(peopleCount, BorderLayout.EAST);
        JList<String> uList = new JList<>(userModel);
        userList = uList;
        themers.add(() -> uList.setBackground(CARD));
        uList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                JLabel lb = (JLabel) super.getListCellRendererComponent(l, v, i, false, false);
                String n = String.valueOf(v);
                lb.setText("<html><font color='#22c55e'>\u25CF</font>&nbsp;&nbsp;" + esc(n)
                        + (n.equals(myNick) ? " <font color='#9ca3af'>(you)</font>" : "") + "</html>");
                lb.setFont(font(Font.PLAIN, 13));
                lb.setForeground(TEXT);
                lb.setBorder(pad(4, 8, 4, 8));
                return lb;
            }
        });
        JScrollPane userScroll = scroll(uList);
        userScroll.setPreferredSize(new Dimension(100, 105));
        people.add(pHead, BorderLayout.NORTH);
        people.add(userScroll, BorderLayout.CENTER);

        JPanel tools = card(new BorderLayout(0, 12));
        JPanel seg = new JPanel(new GridLayout(1, 2, 8, 0));
        seg.setOpaque(false);
        seg.add(tabQ);
        seg.add(tabP);
        tabQ.addActionListener(e -> showTab(true));
        tabP.addActionListener(e -> showTab(false));
        cardHost.setOpaque(false);
        cardHost.add(buildQueue(), "q");
        cardHost.add(buildPoll(), "p");
        tools.add(seg, BorderLayout.NORTH);
        tools.add(cardHost, BorderLayout.CENTER);

        side.add(people, BorderLayout.NORTH);
        side.add(tools, BorderLayout.CENTER);

        workspace.add(conversation, BorderLayout.CENTER);
        workspace.add(side, BorderLayout.EAST);
        return workspace;
    }

    private void showTab(boolean queue) {
        tabQueue = queue;
        cards.show(cardHost, queue ? "q" : "p");
        tabQ.setColors(queue ? PRIMARY : PRIMARY_BG, queue ? Color.WHITE : PRIMARY_DARK);
        tabP.setColors(queue ? PRIMARY_BG : PRIMARY, queue ? PRIMARY_DARK : Color.WHITE);
    }

    class QRenderer extends JPanel implements ListCellRenderer<QItem> {
        private final JLabel badge = new JLabel("", SwingConstants.CENTER);
        private final JLabel body = new JLabel();
        QRenderer() {
            super(new BorderLayout(10, 0));
            badge.setOpaque(true);
            badge.setFont(font(Font.BOLD, 12));
            badge.setPreferredSize(new Dimension(44, 46));
            body.setFont(font(Font.PLAIN, 12));
            add(badge, BorderLayout.WEST);
            add(body, BorderLayout.CENTER);
        }
        @Override public Component getListCellRendererComponent(JList<? extends QItem> list, QItem q, int index,
                                                                 boolean selected, boolean focus) {
            setBackground(selected ? PRIMARY_BG : CARD);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), pad(8, 8, 8, 8)));
            body.setForeground(TEXT);
            badge.setText("<html><center>\u25B2<br>" + q.votes + "</center></html>");
            badge.setBackground(q.done ? GREEN_BG : PRIMARY_BG);
            badge.setForeground(q.done ? GREEN : PRIMARY_FG);
            String t = esc(q.text);
            body.setText("<html><div style='width:215px'>" + (q.done ? "<strike>" + t + "</strike>" : t)
                    + "<br><span style='color:" + hex(MUTED) + ";font-size:10px'>#" + q.id + " \u00B7 asked by " + esc(q.asker)
                    + (q.done ? " \u00B7 <font color='" + hex(GREEN) + "'>\u2714 answered</font>" : "") + "</span></div></html>");
            return this;
        }
    }

    private JPanel buildQueue() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        final String headingHtml = "<html><b>Questions to discuss</b><br><span style='color:#6b7280'>"
                + "Upvote what you need most. Unanswered, most-voted questions stay on top.</span></html>";
        JLabel heading = new JLabel();
        heading.setFont(font(Font.PLAIN, 12));
        themers.add(() -> { heading.setText(themed(headingHtml)); heading.setForeground(TEXT); });
        themers.add(() -> qList.setBackground(CARD));
        qList.setCellRenderer(new QRenderer());
        qList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane queueScroll = scroll(qList);

        JPanel ask = new JPanel(new BorderLayout(6, 0));
        ask.setOpaque(false);
        qAdd.setPreferredSize(new Dimension(70, 34));
        ask.add(qField, BorderLayout.CENTER);
        ask.add(qAdd, BorderLayout.EAST);
        JPanel btns = new JPanel(new GridLayout(1, 3, 6, 0));
        btns.setOpaque(false);
        qVote.setToolTipText("Upvote / remove your upvote on the selected question");
        qDone.setToolTipText("Mark the selected question answered, or reopen it");
        qPoll.setToolTipText("Start a poll about the selected question");
        btns.add(qVote);
        btns.add(qDone);
        btns.add(qPoll);
        JPanel south = new JPanel(new GridLayout(2, 1, 0, 8));
        south.setOpaque(false);
        south.add(ask);
        south.add(btns);

        p.add(heading, BorderLayout.NORTH);
        p.add(queueScroll, BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);

        qAdd.addActionListener(e -> {
            String t = qField.getText().trim();
            if (t.isEmpty()) { addError("Write a question first."); return; }
            sendLine("Q_ADD|" + t);
            qField.setText("");
        });
        qField.addActionListener(e -> qAdd.doClick());
        qVote.addActionListener(e -> { QItem q = qList.getSelectedValue(); if (q != null) sendLine("Q_VOTE|" + q.id); });
        qDone.addActionListener(e -> { QItem q = qList.getSelectedValue(); if (q != null) sendLine("Q_DONE|" + q.id); });
        qPoll.addActionListener(e -> pollSelectedQuestion());
        qList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateQueueActions(); });
        return p;
    }

    private JPanel buildPoll() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        pollLabel.setFont(font(Font.PLAIN, 13));
        themers.add(() -> pollLabel.setForeground(TEXT));
        optPanel.setOpaque(false);
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setOpaque(false);
        JPanel optTop = new JPanel(new BorderLayout());
        optTop.setOpaque(false);
        optTop.add(optPanel, BorderLayout.NORTH);
        view.add(pollLabel, BorderLayout.NORTH);
        view.add(optTop, BorderLayout.CENTER);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 5));
        form.setOpaque(false);
        form.add(fieldLabel("Start a new poll"));
        form.add(pollQ);
        form.add(pollOpts);
        JPanel b = new JPanel(new GridLayout(1, 2, 6, 0));
        b.setOpaque(false);
        b.add(pollStart);
        b.add(pollEnd);
        form.add(b);

        p.add(view, BorderLayout.CENTER);
        p.add(form, BorderLayout.SOUTH);
        pollEnd.addActionListener(e -> sendLine("POLL_END|"));
        return p;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel();
        label.setFont(font(Font.PLAIN, 12));
        themers.add(() -> {
            label.setText(themed("<html><b>" + text + "</b> <span style='color:#6b7280'>(2-5 options separated by ;)</span></html>"));
            label.setForeground(TEXT);
        });
        return label;
    }

    private void styleControls() {
        for (JTextField f : fields()) f.setFont(font(Font.PLAIN, 13));
        themeFields();
        statusLabel.setFont(font(Font.BOLD, 12));
        statusLabel.setBorder(pad(8, 12, 8, 12));
        statusLabel.setOpaque(true);
        setStatus("Offline", HEADER_MUTED, STATUS_OFF_BG);
    }

    private JTextField[] fields() { return new JTextField[]{hostF, portF, nickF, input, qField, pollQ, pollOpts}; }

    private void themeFields() {
        for (JTextField f : fields()) {
            f.setForeground(TEXT);
            f.setCaretColor(TEXT);
            f.setDisabledTextColor(MUTED);
            f.setSelectionColor(PRIMARY);
            f.setSelectedTextColor(Color.WHITE);
            f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), pad(5, 10, 5, 10)));
        }
        refreshFieldBackgrounds();
    }

    private void refreshFieldBackgrounds() {
        for (JTextField f : fields()) f.setBackground(f.isEnabled() ? FIELD : FIELD_OFF);
    }

    private void toggleTheme() {
        applyPalette(!dark);
        try { PREFS.putBoolean("dark", dark); } catch (Exception ignored) {}
        applyTheme();
    }

    private void applyTheme() {
        applyUiDefaults();
        themeB.setText(dark ? "Light mode" : "Dark mode");
        rootPanel.setBackground(BG);
        for (Runnable r : themers) r.run();
        themeFields();

        S_TIME = style(MUTED, 11, false, false);
        S_BODY = style(TEXT, 14, false, false);
        S_SYS = style(MUTED, 12, false, true);
        S_ERR = style(RED, 12, true, false);

        qAdd.setColors(PRIMARY, Color.WHITE);
        sendB.setColors(PRIMARY, Color.WHITE);
        pollStart.setColors(PRIMARY, Color.WHITE);
        pollEnd.setColors(RED, Color.WHITE);
        for (RoundButton b : new RoundButton[]{qVote, qDone, qPoll}) b.setColors(PRIMARY_BG, PRIMARY_DARK);
        connectB.setColors(connectB.getText().equals("Disconnect") ? RED : TEAL, Color.WHITE);
        showTab(tabQueue);

        renderChat();
        renderPoll(lastPoll);
        qList.repaint();
        if (userList != null) userList.repaint();
        SwingUtilities.invokeLater(() -> { getContentPane().revalidate(); getContentPane().repaint(); });
    }

    static class SlimScrollBarUI extends BasicScrollBarUI {
        private JButton zero() {
            JButton b = new JButton();
            Dimension d = new Dimension(0, 0);
            b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
            return b;
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(12, 12); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(CARD);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = dark ? new Color(80, 86, 108) : new Color(200, 204, 212);
            g2.setColor(isDragging ? shade(base, 1) : base);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }

    private void append(String text, AttributeSet a) {
        try { doc.insertString(doc.getLength(), text, a); } catch (BadLocationException ignored) {}
    }

    private void paintLine(ChatLine l) {
        if (l.kind == 1) { append("\u2022 " + l.text + "\n", S_SYS); return; }
        if (l.kind == 2) { append("! " + l.text + "\n", S_ERR); return; }
        append(l.me ? "You" : l.sender, style(l.me ? PRIMARY_FG : colorFor(l.sender), 13, true, false));
        append("   " + l.time + "\n", S_TIME);
        int start = doc.getLength();
        append(l.text + "\n", S_BODY);
        SimpleAttributeSet para = new SimpleAttributeSet();
        StyleConstants.setSpaceBelow(para, 9f);
        doc.setParagraphAttributes(start, 1, para, false);
    }

    private void log(ChatLine l) { history.add(l); paintLine(l); }

    private void renderChat() {
        try { doc.remove(0, doc.getLength()); } catch (BadLocationException ignored) {}
        doc.setParagraphAttributes(0, 1, new SimpleAttributeSet(), true);
        for (ChatLine l : history) paintLine(l);
        chat.setCaretPosition(doc.getLength());
    }

    private void addChat(String time, String sender, String text) {
        ChatLine l = new ChatLine();
        l.kind = 0; l.time = time; l.sender = sender; l.text = text; l.me = sender.equals(myNick);
        log(l);
    }

    private void addSystem(String text) { ChatLine l = new ChatLine(); l.kind = 1; l.text = text; log(l); }
    private void addError(String text) { ChatLine l = new ChatLine(); l.kind = 2; l.text = text; log(l); }

    private void wireSend() {
        java.awt.event.ActionListener al = e -> {
            String t = input.getText().trim();
            if (t.isEmpty()) { addError("Blank messages are not sent."); return; }
            if (!connected) return;
            if (t.length() > MAX_MSG) { addError("Message too long (max " + MAX_MSG + " characters)."); return; }
            sendLine("MSG|" + t);
            input.setText("");
        };
        sendB.addActionListener(al);
        input.addActionListener(al);
    }

    private List<String> splitOptions(String raw) {
        List<String> options = new ArrayList<>();
        for (String option : raw.split(";")) if (!option.trim().isEmpty()) options.add(option.trim());
        return options;
    }

    private void startPoll() {
        String question = pollQ.getText().trim();
        List<String> options = splitOptions(pollOpts.getText());
        if (question.isEmpty() || options.size() < 2 || options.size() > 5) {
            error("Enter a poll question and 2 to 5 options separated by semicolons.");
            return;
        }
        sendLine("POLL_START|" + question + "|" + String.join(";", options));
        pollQ.setText("");
        pollOpts.setText("");
    }

    private void pollSelectedQuestion() {
        QItem question = qList.getSelectedValue();
        if (question == null || question.done || pollState == 1) return;
        String entered = (String) JOptionPane.showInputDialog(this,
                "Enter 2 to 5 poll options separated by semicolons:",
                "Poll this question", JOptionPane.PLAIN_MESSAGE, null, null, "Yes;No;Not sure");
        if (entered == null) return;
        List<String> options = splitOptions(entered);
        if (options.size() < 2 || options.size() > 5) {
            error("Enter between 2 and 5 non-empty options separated by semicolons.");
            return;
        }
        sendLine("POLL_FROMQ|" + question.id + "|" + String.join(";", options));
        showTab(false);
    }

    private void setUi(boolean on) {
        for (JComponent c : new JComponent[]{input, sendB, qField, qAdd, pollQ, pollOpts}) c.setEnabled(on);
        hostF.setEnabled(!on);
        portF.setEnabled(!on);
        nickF.setEnabled(!on);
        connectB.setText(on ? "Disconnect" : "Connect");
        connectB.setColors(on ? RED : TEAL, Color.WHITE);
        pollStart.setEnabled(on && pollState != 1);
        pollEnd.setEnabled(on && pollState == 1);
        refreshFieldBackgrounds();
        updateQueueActions();
    }

    private void updateQueueActions() {
        QItem selected = qList.getSelectedValue();
        boolean has = connected && selected != null;
        qVote.setEnabled(has);
        qDone.setEnabled(has);
        qPoll.setEnabled(has && !selected.done && pollState != 1);
    }

    private int parsePort(String s) {
        try {
            int p = Integer.parseInt(s.trim());
            return (p >= 1 && p <= 65535) ? p : -1;
        } catch (NumberFormatException e) { return -1; }
    }

    private void connect() {
        final String host = hostF.getText().trim();
        final String nick = nickF.getText().trim();
        final int port = parsePort(portF.getText());
        if (host.isEmpty()) { error("Enter the server IP address."); return; }
        if (port < 0) { error("Port must be a number from 1 to 65535."); return; }
        if (nick.isEmpty()) { error("Enter a nickname."); return; }
        welcomed = false;
        connectB.setEnabled(false);
        setStatus("Connecting...", new Color(146, 64, 14), new Color(254, 243, 199));
        addSystem("Connecting to " + host + ":" + port + "...");
        qMap.clear(); qModel.clear(); userModel.clear(); peopleCount.setText("0");
        myNick = ""; myVote = -1; pollKey = "";
        renderPoll(new String[]{"0", "", "", "", "-1"});
        new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, port), 4000);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                sock = s;
                out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
                connected = true;
                welcomed = false;
                leaving = false;
                out.println("NICK|" + nick);
                String line;
                while ((line = in.readLine()) != null) {
                    final String l = line;
                    if (l.equals("SYS|Goodbye!")) leaving = true;
                    SwingUtilities.invokeLater(() -> handle(l));
                }
                if (connected && !leaving) SwingUtilities.invokeLater(() -> addError("Connection to the server was lost."));
            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> error("Unknown host: " + host));
            } catch (IOException e) {
                if (!welcomed) SwingUtilities.invokeLater(() -> error("Cannot reach server " + host + ":" + port
                        + "  (" + e.getMessage() + ")\nCheck the IP, port and that the server is running."));
            } finally {
                closeQuiet();
                SwingUtilities.invokeLater(() -> {
                    setUi(false);
                    connectB.setEnabled(true);
                    userModel.clear();
                    peopleCount.setText("0");
                    setStatus("Offline", HEADER_MUTED, STATUS_OFF_BG);
                });
            }
        }, "net-reader").start();
    }

    private void sendLine(String line) {
        PrintWriter target = out;
        if (!connected || target == null) return;
        networkWriter.execute(() -> {
            target.println(line);
            if (target.checkError() && connected && target == out) {
                closeSocketQuiet(sock);
                SwingUtilities.invokeLater(() -> addError("Message could not be sent; the connection was closed."));
            }
        });
    }

    private void disconnect() {
        PrintWriter target = out;
        connected = false;
        connectB.setEnabled(false);
        setStatus("Disconnecting...", HEADER_MUTED, STATUS_OFF_BG);
        addSystem("You left the huddle.");
        networkWriter.execute(() -> {
            if (target != null) target.println("MSG|/exit");
            closeQuiet();
        });
    }

    private void closeQuiet() { connected = false; closeSocketQuiet(sock); }

    private void closeSocketQuiet(Socket socket) {
        if (socket != null) try { socket.close(); } catch (IOException ignored) {}
    }

    private void error(String m) {
        addError(m.replace("\n", " "));
        JOptionPane.showMessageDialog(this, m, "HuddleUp", JOptionPane.WARNING_MESSAGE);
        connectB.setEnabled(true);
        if (!connected) setStatus("Offline", HEADER_MUTED, STATUS_OFF_BG);
    }

    private void setStatus(String text, Color fg, Color bg) {
        statusLabel.setText(text);
        statusLabel.setForeground(fg);
        statusLabel.setBackground(bg);
    }

    private void handle(String line) {
        try {
            String[] p = line.split("\\|", 2);
            String type = p[0], rest = p.length > 1 ? p[1] : "";
            switch (type) {
                case "OK":
                    welcomed = true;
                    myNick = rest;
                    nickF.setText(rest);
                    nickF.setEnabled(false);
                    setUi(true);
                    connectB.setEnabled(true);
                    setStatus("Online \u00B7 " + rest, new Color(21, 128, 61), STATUS_ON_BG);
                    break;
                case "ERR":
                    addError(rest);
                    if (!welcomed) {
                        error(rest);
                        connected = false;
                        connectB.setEnabled(false);
                        Socket rejected = sock;
                        networkWriter.execute(() -> closeSocketQuiet(rejected));
                    }
                    break;
                case "SYS": addSystem(rest); break;
                case "CHAT": {
                    String[] c = rest.split("\\|", 3);
                    if (c.length == 3) addChat(c[0], c[1], c[2]);
                    break; }
                case "USERS":
                    userModel.clear();
                    if (!rest.isEmpty()) for (String u : rest.split(",")) userModel.addElement(u);
                    peopleCount.setText(String.valueOf(userModel.size()));
                    break;
                case "QUPDATE": {
                    String[] f = rest.split("\\|", 5);
                    int id = Integer.parseInt(f[0]);
                    QItem q = qMap.computeIfAbsent(id, k -> new QItem());
                    q.id = id;
                    q.votes = Integer.parseInt(f[1]);
                    q.done = f[2].equals("1");
                    q.asker = f[3];
                    q.text = f[4];
                    refreshQueue();
                    break; }
                case "POLL": renderPoll(rest.split("\\|", -1)); break;
                default: break;
            }
        } catch (RuntimeException ex) {
            addError("Ignored a malformed message from the server.");
        }
        chat.setCaretPosition(doc.getLength());
    }

    private void refreshQueue() {
        QItem cur = qList.getSelectedValue();
        int sel = cur == null ? -1 : cur.id;
        List<QItem> l = new ArrayList<>(qMap.values());
        l.sort(Comparator.comparing((QItem q) -> q.done).thenComparing(q -> -q.votes).thenComparing(q -> q.id));
        qModel.clear();
        for (QItem q : l) {
            qModel.addElement(q);
            if (q.id == sel) qList.setSelectedIndex(qModel.size() - 1);
        }
        updateQueueActions();
    }

    private void renderPoll(String[] f) {
        lastPoll = f;
        int prev = pollState;
        int state = Integer.parseInt(f[0]);
        String key = f[1] + "|" + f[2] + "|" + (f.length > 4 ? f[4] : "-1");
        if (!key.equals(pollKey) || (state == 1 && prev != 1)) myVote = -1;
        pollKey = key;
        pollState = state;
        optPanel.removeAll();
        pollStart.setEnabled(connected && state != 1);
        pollEnd.setEnabled(connected && state == 1);
        updateQueueActions();

        if (state == 0) {
            pollLabel.setText(themed("<html><div style='width:300px'><b>No poll running</b><br><span style='color:#6b7280'>"
                    + "Start one below, or press \"Poll it\" on a question in the queue.</span></div></html>"));
        } else {
            String[] o = f[2].split(";"), c = f[3].split(",");
            int n = Math.min(o.length, c.length);
            int[] counts = new int[n];
            int total = 0, max = 0;
            for (int i = 0; i < n; i++) {
                counts[i] = Integer.parseInt(c[i].trim());
                total += counts[i];
                max = Math.max(max, counts[i]);
            }
            int questionId = f.length > 4 ? Integer.parseInt(f[4].trim()) : -1;
            String origin = questionId >= 0 ? "<br><span style='color:#6b7280'>From question #" + questionId + "</span>" : "";
            String status = state == 2 ? "<br><span style='color:#16a34a'><b>Closed</b> \u2013 final results</span>"
                    : "<br><span style='color:#6b7280'>" + total + " vote(s) so far</span>";
            pollLabel.setText(themed("<html><div style='width:300px'><b style='font-size:14px'>" + esc(f[1]) + "</b>"
                    + origin + status + "</div></html>"));

            for (int i = 0; i < n; i++) {
                final int idx = i;
                int pct = total == 0 ? 0 : Math.round(100f * counts[i] / total);
                JLabel name = new JLabel(themed("<html><b>" + esc(o[i]) + "</b>&nbsp;&nbsp;<span style='color:#6b7280'>"
                        + counts[i] + (counts[i] == 1 ? " vote" : " votes") + " \u00B7 " + pct + "%</span></html>"));
                name.setFont(font(Font.PLAIN, 12));
                name.setForeground(TEXT);
                JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
                bar.setValue(counts[i]);
                bar.setBorderPainted(false);
                bar.setStringPainted(false);
                bar.setBackground(BORDER);
                bar.setForeground(state == 2 && max > 0 && counts[i] == max ? GREEN : PRIMARY);
                bar.setPreferredSize(new Dimension(100, 10));
                JPanel mid = new JPanel(new BorderLayout(0, 5));
                mid.setOpaque(false);
                mid.add(name, BorderLayout.NORTH);
                mid.add(bar, BorderLayout.CENTER);
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);
                row.add(mid, BorderLayout.CENTER);
                if (state == 1) {
                    boolean mine = idx == myVote;
                    RoundButton vb = new RoundButton(mine ? "Voted \u2714" : "Vote", mine ? new Color(22, 163, 74) : PRIMARY, Color.WHITE);
                    vb.setPreferredSize(new Dimension(84, 30));
                    vb.setEnabled(connected);
                    vb.addActionListener(e -> { myVote = idx; sendLine("POLL_VOTE|" + idx); });
                    row.add(vb, BorderLayout.EAST);
                }
                optPanel.add(row);
            }
        }
        optPanel.revalidate();
        optPanel.repaint();
    }

    public static void main(String[] a) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception ignored) {}
        applyUiDefaults();
        SwingUtilities.invokeLater(() -> new HuddleClient().setVisible(true));
    }
}