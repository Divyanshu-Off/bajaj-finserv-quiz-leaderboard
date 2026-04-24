import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * QuizLeaderboard.java
 *
 * Bajaj Finserv Health - Java Qualifier Assignment (SRM, April 2024)
 *
 * How it works:
 *  1. Polls GET /quiz/messages?regNo=<REG_NO>&poll=0..9  (10 times, 5s delay each)
 *  2. Deduplicates events by composite key: roundId + "|" + participant
 *  3. Aggregates totalScore per participant
 *  4. Sorts leaderboard by totalScore descending
 *  5. Submits the result once via POST /quiz/submit
 *
 * No external libraries needed. Compile and run with plain javac/java.
 */
public class QuizLeaderboard {

    // ── CONFIG ── update REG_NO before running ──────────────────────────────
    private static final String BASE_URL    = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO      = "2024CS101"; // <-- change to your reg number
    private static final int    TOTAL_POLLS = 10;
    private static final long   DELAY_MS    = 5_000L;     // 5 seconds between polls
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {

        System.out.println("==============================================");
        System.out.println("  Bajaj Finserv Health - Quiz Leaderboard");
        System.out.println("==============================================");
        System.out.println("Registration No : " + REG_NO);
        System.out.println("Total Polls     : " + TOTAL_POLLS);
        System.out.println();

        // STEP 1 - Poll API 10 times
        List<String> rawResponses = new ArrayList<>();
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            System.out.print("Poll " + poll + " ... ");
            String response = httpGet(url);
            rawResponses.add(response);
            System.out.println("OK");

            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting 5 seconds...");
                Thread.sleep(DELAY_MS);
            }
        }

        // STEP 2 - Parse + Deduplicate  (key = "roundId|participant")
        // Using LinkedHashMap keeps insertion order; putIfAbsent ignores duplicates
        Map<String, Integer> dedupMap = new LinkedHashMap<>();
        for (String response : rawResponses) {
            parseEvents(response, dedupMap);
        }
        System.out.println("\nUnique events after deduplication: " + dedupMap.size());

        // STEP 3 - Aggregate scores per participant
        Map<String, Integer> scoreMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : dedupMap.entrySet()) {
            String participant = entry.getKey().split("\\|")[1];
            scoreMap.merge(participant, entry.getValue(), Integer::sum);
        }

        // STEP 4 - Sort leaderboard descending by totalScore
        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scoreMap.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());

        // Print leaderboard to console
        int combinedTotal = 0;
        System.out.println("\n==============================================");
        System.out.println("  LEADERBOARD");
        System.out.println("==============================================");
        System.out.printf("%-6s %-20s %s%n", "Rank", "Participant", "Total Score");
        System.out.println("----------------------------------------------");
        int rank = 1;
        for (Map.Entry<String, Integer> e : leaderboard) {
            System.out.printf("%-6d %-20s %d%n", rank++, e.getKey(), e.getValue());
            combinedTotal += e.getValue();
        }
        System.out.println("----------------------------------------------");
        System.out.println("Combined Total Score: " + combinedTotal);
        System.out.println("==============================================\n");

        // STEP 5 - Submit once
        submit(leaderboard);
    }

    // ── Parse events array from one poll response and deduplicate ────────────
    private static void parseEvents(String json, Map<String, Integer> dedupMap) {
        int evStart = json.indexOf("\"events\"");
        if (evStart == -1) return;

        int arrOpen  = json.indexOf('[', evStart);
        int arrClose = json.lastIndexOf(']');
        if (arrOpen == -1 || arrClose == -1) return;

        String body = json.substring(arrOpen + 1, arrClose);

        // Split into individual { } objects
        int depth = 0, start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') { if (depth == 0) start = i; depth++; }
            else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    processEvent(body.substring(start, i + 1), dedupMap);
                    start = -1;
                }
            }
        }
    }

    private static void processEvent(String obj, Map<String, Integer> dedupMap) {
        String roundId     = field(obj, "roundId");
        String participant = field(obj, "participant");
        int    score       = intField(obj, "score");

        if (roundId == null || participant == null) return;

        // putIfAbsent → first occurrence wins; later duplicates are ignored
        dedupMap.putIfAbsent(roundId + "|" + participant, score);
    }

    // ── Submit leaderboard ───────────────────────────────────────────────────
    private static void submit(List<Map.Entry<String, Integer>> leaderboard) throws IOException {
        StringBuilder body = new StringBuilder();
        body.append("{\"regNo\":\"").append(REG_NO).append("\",\"leaderboard\":[");
        for (int i = 0; i < leaderboard.size(); i++) {
            Map.Entry<String, Integer> e = leaderboard.get(i);
            body.append("{\"participant\":\"").append(e.getKey())
                .append("\",\"totalScore\":").append(e.getValue()).append("}");
            if (i < leaderboard.size() - 1) body.append(",");
        }
        body.append("]}");

        System.out.println("Submitting...");
        System.out.println("Request: " + body);

        String response = httpPost(BASE_URL + "/quiz/submit", body.toString());
        System.out.println("Response: " + response);
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────
    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        if (conn.getResponseCode() != 200)
            throw new IOException("GET " + urlStr + " -> HTTP " + conn.getResponseCode());
        return readAll(conn.getInputStream());
    }

    private static String httpPost(String urlStr, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(is);
        if (status < 200 || status >= 300)
            throw new IOException("POST returned HTTP " + status + ": " + response);
        return response;
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    // ── Minimal JSON field extractors (no external libs needed) ─────────────
    private static String field(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i == -1) return null;
        int colon = json.indexOf(':', i + k.length());
        if (colon == -1) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 == -1) return null;
        int q2 = json.indexOf('"', q1 + 1);
        return q2 == -1 ? null : json.substring(q1 + 1, q2);
    }

    private static int intField(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i == -1) return 0;
        int colon = json.indexOf(':', i + k.length());
        if (colon == -1) return 0;
        StringBuilder sb = new StringBuilder();
        for (int j = colon + 1; j < json.length(); j++) {
            char c = json.charAt(j);
            if (Character.isDigit(c) || (c == '-' && sb.length() == 0)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        return sb.length() > 0 ? Integer.parseInt(sb.toString()) : 0;
    }
}
