import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class HuddleServer {
    static final int MAX_MSG = 500;
    static final int MAX_QUESTION = 200;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    static class Question { int id; String asker, text; Set<String> voters = new HashSet<>(); boolean done; }

    private final Object lock = new Object();
    private final Map<String, Handler> users = new LinkedHashMap<>();
    private final Map<Integer, Question> questions = new LinkedHashMap<>();
    private final Deque<String> chatHistory = new ArrayDeque<>();
    private int nextQ = 1;
    private int pollState = 0;
    private String pollQ = "";
    private int pollQuestionId = -1;
    private List<String> pollOpts = new ArrayList<>();
    private final Map<String, Integer> pollVotes = new HashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public static void main(String[] a) {
        int port = 5000;
        if (a.length > 0) {
            try { port = Integer.parseInt(a[0].trim()); }
            catch (NumberFormatException e) { System.err.println("Invalid port: " + a[0]); return; }
        }
        if (port < 1 || port > 65535) { System.err.println("Port must be 1-65535."); return; }
        try { new HuddleServer().start(port); }
        catch (IOException e) { System.err.println("Could not start server on port " + port + ": " + e.getMessage()); }
    }

    static void log(String s) { System.out.println("[" + LocalTime.now().format(TIME) + "] " + s); }

    void start(int port) throws IOException {
        try (ServerSocket ss = new ServerSocket(port)) {
            log("HuddleUp server listening on port " + port);
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress ia : Collections.list(ni.getInetAddresses()))
                    if (ia instanceof Inet4Address && !ia.isLoopbackAddress())
                        log("  LAN address (give this to the other clients): " + ia.getHostAddress());
            while (true) { Socket s = ss.accept(); pool.execute(new Handler(s)); }
        }
    }

    void broadcast(String line) { for (Handler h : users.values()) h.send(line); }
    void sendUsers() { broadcast("USERS|" + String.join(",", users.keySet())); }
    String qLine(Question q) { return "QUPDATE|" + q.id + "|" + q.voters.size() + "|" + (q.done ? 1 : 0) + "|" + q.asker + "|" + q.text; }
    String pollLine() {
        if (pollState == 0) return "POLL|0||||-1";
        int[] c = new int[pollOpts.size()];
        for (int v : pollVotes.values()) c[v]++;
        StringBuilder cs = new StringBuilder();
        for (int i = 0; i < c.length; i++) cs.append(i > 0 ? "," : "").append(c[i]);
        return "POLL|" + pollState + "|" + pollQ + "|" + String.join(";", pollOpts) + "|" + cs + "|" + pollQuestionId;
    }
    static String clean(String s) { return s.replace("|", "/").replace(";", ",").trim(); }
    static List<String> parseOptions(String text) {
        List<String> options = new ArrayList<>();
        for (String option : text.split(";")) {
            String cleaned = clean(option);
            if (!cleaned.isEmpty()) options.add(cleaned);
        }
        return options;
    }

    void startPoll(String question, List<String> options, int questionId) {
        pollState = 1;
        pollQ = question;
        pollQuestionId = questionId;
        pollOpts = options;
        pollVotes.clear();
    }

    void announcePollResult() {
        int[] counts = new int[pollOpts.size()];
        for (int vote : pollVotes.values()) counts[vote]++;
        int highest = 0;
        for (int count : counts) highest = Math.max(highest, count);
        if (highest == 0) { broadcast("SYS|Poll closed with no votes."); return; }
        List<String> winners = new ArrayList<>();
        for (int i = 0; i < counts.length; i++)
            if (counts[i] == highest) winners.add(pollOpts.get(i));
        if (winners.size() == 1)
            broadcast("SYS|Poll winner: " + winners.get(0) + " (" + highest + " vote(s)).");
        else
            broadcast("SYS|Poll ended in a tie between " + String.join(", ", winners) + " (" + highest + " vote(s) each).");
    }

    class Handler implements Runnable {
        static final String STOP = "\u0000STOP";
        final Socket sock;
        BufferedReader in;
        PrintWriter out;
        volatile String nick;
        final BlockingQueue<String> outbox = new LinkedBlockingQueue<>();
        volatile boolean writerStarted;

        Handler(Socket s) { sock = s; }

        void send(String line) {
            if (outbox.size() > 2000) { closeSock(); return; }
            outbox.offer(line);
        }

        void startWriter() {
            writerStarted = true;
            Thread t = new Thread(() -> {
                try {
                    while (true) {
                        String l = outbox.take();
                        if (STOP.equals(l)) break;
                        out.println(l);
                        if (out.checkError()) break;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                closeSock();
            }, "writer");
            t.setDaemon(true);
            t.start();
        }

        void closeSock() { try { sock.close(); } catch (IOException ignored) {} }

        public void run() {
            String remote = String.valueOf(sock.getRemoteSocketAddress());
            try {
                sock.setSoTimeout(15000);
                in = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(sock.getOutputStream(), "UTF-8"), true);
                startWriter();
                String first = in.readLine();
                if (first == null) return;
                if (!first.startsWith("NICK|")) { send("ERR|Protocol error: expected NICK first."); return; }
                sock.setSoTimeout(0);
                String err;
                synchronized (lock) {
                    String n = first.substring(5).trim();
                    err = checkNick(n, null);
                    if (err == null) {
                        nick = n; users.put(nick, this);
                        send("OK|" + nick);
                        send("SYS|Welcome to HuddleUp! Type /help for commands.");
                        for (Question q : questions.values()) send(qLine(q));
                        send(pollLine());
                        sendUsers();
                        for (String chatLine : chatHistory) send(chatLine);
                        broadcast("SYS|" + nick + " joined the huddle");
                        log(nick + " joined from " + remote + "  (" + users.size() + " online)");
                    }
                }
                if (err != null) { send("ERR|" + err); log("Rejected " + remote + ": " + err); return; }
                String line;
                while ((line = in.readLine()) != null) { if (!handle(line)) break; }
            } catch (IOException e) {
            } finally { cleanup(); }
        }

        String checkNick(String n, String self) {
            if (n.isEmpty()) return "Nickname cannot be blank.";
            if (!n.matches("[A-Za-z0-9_-]{1,16}")) return "Nickname must be 1-16 letters, digits, _ or -.";
            for (String u : users.keySet()) if (u.equalsIgnoreCase(n) && !u.equals(self)) return "Nickname '" + n + "' is already in use.";
            return null;
        }

        boolean handle(String line) {
            String[] p = line.split("\\|", 2);
            String type = p[0], rest = p.length > 1 ? p[1].trim() : "";
            synchronized (lock) {
                switch (type) {
                    case "MSG":
                        if (rest.isEmpty()) { send("ERR|Blank messages are not allowed."); break; }
                        if (rest.startsWith("/")) return command(rest);
                        if (rest.length() > MAX_MSG) { send("ERR|Message too long (max " + MAX_MSG + " characters)."); break; }
                        String chatLine = "CHAT|" + LocalTime.now().format(TIME) + "|" + nick + "|" + rest;
                        chatHistory.addLast(chatLine);
                        if (chatHistory.size() > 50) chatHistory.removeFirst();
                        broadcast(chatLine);
                        break;
                    case "Q_ADD": {
                        String t = clean(rest);
                        if (t.isEmpty()) { send("ERR|Question cannot be blank."); break; }
                        if (t.length() > MAX_QUESTION) { send("ERR|Question too long (max " + MAX_QUESTION + " characters)."); break; }
                        Question q = new Question(); q.id = nextQ++; q.asker = nick; q.text = t;
                        questions.put(q.id, q);
                        broadcast(qLine(q)); broadcast("SYS|" + nick + " added question #" + q.id);
                        break; }
                    case "Q_VOTE": case "Q_DONE": {
                        Question q = null;
                        try { q = questions.get(Integer.parseInt(rest)); } catch (NumberFormatException ignored) {}
                        if (q == null) { send("ERR|Unknown question."); break; }
                        if (type.equals("Q_VOTE")) { if (!q.voters.add(nick)) q.voters.remove(nick); }
                        else { q.done = !q.done; broadcast("SYS|" + nick + (q.done ? " marked" : " reopened") + " question #" + q.id); }
                        broadcast(qLine(q));
                        break; }
                    case "POLL_START": {
                        String[] f = rest.split("\\|", 2);
                        String q = clean(f[0]);
                        List<String> opts = f.length > 1 ? parseOptions(f[1]) : new ArrayList<>();
                        if (q.isEmpty() || opts.size() < 2 || opts.size() > 5) { send("ERR|A poll needs a question and 2-5 options."); break; }
                        if (q.length() > MAX_QUESTION) { send("ERR|Poll question too long."); break; }
                        if (pollState == 1) { send("ERR|A poll is already running. End it first."); break; }
                        startPoll(q, opts, -1);
                        broadcast(pollLine()); broadcast("SYS|" + nick + " started a poll: " + q);
                        break; }
                    case "POLL_FROMQ": {
                        String[] f = rest.split("\\|", 2);
                        int questionId;
                        try {
                            if (f.length < 2) throw new NumberFormatException();
                            questionId = Integer.parseInt(f[0]);
                        } catch (NumberFormatException e) {
                            send("ERR|Invalid question for poll.");
                            break;
                        }
                        Question q = questions.get(questionId);
                        if (q == null) { send("ERR|Unknown question."); break; }
                        if (q.done) { send("ERR|That question is already answered."); break; }
                        if (pollState == 1) { send("ERR|A poll is already running. End it first."); break; }
                        List<String> opts = parseOptions(f[1]);
                        if (opts.size() < 2 || opts.size() > 5) { send("ERR|A poll needs 2-5 options."); break; }
                        startPoll(q.text, opts, q.id);
                        broadcast(pollLine());
                        broadcast("SYS|" + nick + " started a poll from question #" + q.id + ": " + q.text);
                        break; }
                    case "POLL_VOTE":
                        try {
                            int i = Integer.parseInt(rest);
                            if (pollState != 1 || i < 0 || i >= pollOpts.size()) { send("ERR|No active poll option."); break; }
                            pollVotes.put(nick, i); broadcast(pollLine());
                        } catch (NumberFormatException e) { send("ERR|Invalid vote."); }
                        break;
                    case "POLL_END":
                        if (pollState != 1) { send("ERR|No active poll."); break; }
                        pollState = 2;
                        broadcast(pollLine());
                        broadcast("SYS|" + nick + " closed the poll");
                        announcePollResult();
                        if (pollQuestionId >= 0) {
                            Question q = questions.get(pollQuestionId);
                            if (q != null) { q.done = true; broadcast(qLine(q)); }
                        }
                        break;
                    default: send("ERR|Unknown command from client.");
                }
            }
            return true;
        }

        boolean command(String text) {
            String[] w = text.split("\\s+", 2);
            switch (w[0].toLowerCase()) {
                case "/help":
                    send("SYS|Commands: /name <newname> - change nickname | /exit - leave | /help - this list");
                    break;
                case "/exit":
                    send("SYS|Goodbye!");
                    return false;
                case "/name": {
                    String n = w.length > 1 ? w[1].trim() : "";
                    String err = checkNick(n, nick);
                    if (err != null) { send("ERR|" + err); break; }
                    String old = nick;
                    users.remove(old); nick = n; users.put(nick, this);
                    if (pollVotes.containsKey(old)) pollVotes.put(nick, pollVotes.remove(old));
                    for (Question q : questions.values()) if (q.voters.remove(old)) q.voters.add(nick);
                    send("OK|" + nick);
                    broadcast("SYS|" + old + " is now known as " + nick);
                    sendUsers();
                    log(old + " renamed to " + nick);
                    break; }
                default: send("ERR|Unknown command. Type /help.");
            }
            return true;
        }

        void cleanup() {
            synchronized (lock) {
                if (nick != null && users.get(nick) == this) {
                    users.remove(nick);
                    if (pollVotes.remove(nick) != null) broadcast(pollLine());
                    broadcast("SYS|" + nick + " left the huddle");
                    sendUsers();
                    log(nick + " left  (" + users.size() + " online)");
                }
            }
            outbox.offer(STOP);
            if (!writerStarted) closeSock();
        }
    }
}