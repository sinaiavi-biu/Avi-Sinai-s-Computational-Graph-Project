package tests;

import graph.TopicManagerSingleton;
import graph.Agent;
import graph.Message;
import graph.ParallelAgent;
import server.MyHTTPServer;
import servlets.BulkTopicDisplayer;
import servlets.ConfLoader;
import servlets.GraphDisplayer;
import servlets.HtmlLoader;
import servlets.TopicDisplayer;
import servlets.TopicFormDisplayer;
import views.HtmlGraphWriter;

import graph.Graph;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Assignment6EdgeCaseTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        System.out.println("Assignment 6 visible edge-case test report");
        System.out.println("=========================================");

        htmlGraphWriterShowsTopicsAgentsAndArrows();
        httpServerLoadsIndexAndMissingFile();
        uploadConfigBuildsGraphAndPublishDisplaysValues();
        bulkPublishPublishesExistingAndCustomTopics();
        multiOutputAgentShowsAndPublishesAllOutputs();
        booleanAndMathFamiliesCanBeAdded();
        variableInputAgentsCanBeBuilt();
        invalidUploadReturnsHtmlError();
        parallelAgentCloseDoesNotBlockWhenQueueIsFull();
        serverCloseClosesConfigThreads();

        System.out.println();
        System.out.println("RESULT: PASSED " + passed + " / " + passed + " checks.");
    }

    private static void htmlGraphWriterShowsTopicsAgentsAndArrows() {
        section("HtmlGraphWriter renders graph shapes");
        TopicManagerSingleton.get().clear();
        configs.MathExampleConfig config = new configs.MathExampleConfig();
        config.create();
        Graph graph = new Graph();
        graph.createFromTopics();

        String html = String.join("\n", HtmlGraphWriter.getGraphHTML(graph));
        check(html.contains("<svg"), "graph HTML contains SVG", "svg", "present");
        check(html.contains("class=\"topic\""), "topic nodes are rectangles", "class=\"topic\"", "present");
        check(html.contains("class=\"agent\""), "agent nodes are ellipses", "class=\"agent\"", "present");
        check(html.contains("class=\"edge\""), "directed edges are drawn", "class=\"edge\"", "present");
        check(html.contains(">A<") && html.contains(">plus<"), "display labels hide graph prefixes", "A and plus",
                "present");
        config.close();
        TopicManagerSingleton.get().clear();
    }

    private static void bulkPublishPublishesExistingAndCustomTopics() throws Exception {
        section("Bulk publish form and custom topic");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String conf = "configs.PlusAgent\nA,B\nC\n";
        send(port, multipartRequest("/upload", "plus.conf", conf));

        String form = send(port, "GET /topic-form HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(form.contains("name=\"topic0\"") && form.contains("customTopic"),
                "topic form includes existing topics and custom topic controls", "topic rows plus custom",
                responseBody(form));
        check(!form.contains("value=\"C\""), "topic form hides computed topics", "no C input",
                responseBody(form));

        String response = send(port,
                "GET /publishAll?count=2&topic0=A&message0=2&topic1=B&message1=3&customTopic=EXTRA&customMessage=hello HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(response.contains("<td>A</td><td>2</td>") && response.contains("<td>B</td><td>3</td>"),
                "bulk publish updates multiple existing topics", "A=2 and B=3", responseBody(response));
        check(response.contains("<td>C</td><td>5.0</td>"), "bulk publish triggers configured computation", "C=5.0",
                responseBody(response));
        check(response.contains("<td>EXTRA</td><td>hello</td>"), "bulk publish creates custom topic", "EXTRA=hello",
                responseBody(response));

        String graph = send(port, "GET /graph HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(graph.contains(">EXTRA<"), "custom topic appears on graph", "EXTRA node", responseBody(graph));

        String blocked = send(port, "GET /publish?topic=C&message=999 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(blocked.contains("cannot be manually published") && !blocked.contains("<td>C</td><td>999</td>"),
                "manual publish to computed topic is rejected", "blocked C=999", responseBody(blocked));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void httpServerLoadsIndexAndMissingFile() throws Exception {
        section("HtmlLoader serves static files");
        int port = freePort();
        MyHTTPServer server = new MyHTTPServer(port, 2);
        server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
        server.start();
        Thread.sleep(150);

        String index = send(port, "GET /app/index.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(index.contains("HTTP/1.1 200 OK"), "index request returns valid HTTP response", "200", status(index));
        check(index.contains("/app/form.html") && index.contains("/publish"),
                "index contains required iframe sources", "form and live values iframes", responseBody(index));

        String missing = send(port, "GET /app/missing.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(missing.contains("File not found"), "missing HTML file returns friendly page", "File not found",
                responseBody(missing));
        server.close();
        server.join(2000);
    }

    private static void multiOutputAgentShowsAndPublishesAllOutputs() throws Exception {
        section("multi-output agents stay visible and computed");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String conf = "configs.PlusAgent\nA,B\nC,D\n";
        String upload = send(port, multipartRequest("/upload", "multi-output.conf", conf));
        check(upload.contains(">A+B=C,D<"), "agent label includes every configured output", "A+B=C,D",
                responseBody(upload));

        String response = send(port,
                "GET /publishAll?count=2&topic0=A&message0=2&topic1=B&message1=3 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(response.contains("<td>C</td><td>5.0</td>") && response.contains("<td>D</td><td>5.0</td>"),
                "multi-output PlusAgent publishes the result to all outputs", "C=5.0 and D=5.0",
                responseBody(response));

        String graph = send(port, "GET /graph HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(graph.contains(">A+B=C,D<") && graph.contains(">C<") && graph.contains(">D<"),
                "graph keeps both output arrows and the full multi-output label", "A+B=C,D with C and D",
                responseBody(graph));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void uploadConfigBuildsGraphAndPublishDisplaysValues() throws Exception {
        section("ConfLoader upload and TopicDisplayer publish");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String conf = "configs.PlusAgent\nA,B\nC\nconfigs.IncAgent\nC\nD\n";
        String upload = send(port, multipartRequest("/upload", "simple.conf", conf));
        check(upload.contains("HTTP/1.1 200 OK"), "upload returns valid HTTP response", "200", status(upload));
        check(upload.contains(">A<") && upload.contains(">A+B=C<") && upload.contains(">D<"),
                "generated graph includes uploaded config topics and agents", "A, A+B=C, D",
                responseBody(upload));

        String publishA = send(port, "GET /publish?topic=A&message=4 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(publishA.contains("<td>A</td><td>4</td>"), "publishing A appears in topic table", "A=4",
                responseBody(publishA));

        String publishB = send(port, "GET /publish?topic=B&message=6 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(publishB.contains("<td>B</td><td>6</td>"), "publishing B appears in topic table", "B=6",
                responseBody(publishB));
        check(publishB.contains("refresh-topic-form") && !publishB.contains("setTimeout"),
                "topic table refresh is event-driven, not timer-driven", "postMessage refresh", responseBody(publishB));
        check(publishB.contains("<td>C</td><td>10.0</td>"), "PlusAgent computes C=A+B", "C=10.0",
                responseBody(publishB));
        check(publishB.contains("<td>D</td><td>11.0</td>"), "IncAgent computes D=C+1", "D=11.0",
                responseBody(publishB));

        String graphAfterPublish = send(port, "GET /graph HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(graphAfterPublish.contains("class=\"edge-label\"") && graphAfterPublish.contains(">10.0<"),
                "graph refresh shows transferred values on edges", "edge label 10.0", responseBody(graphAfterPublish));

        String inlineConf = "configs.PlusAgent\r\nA,B\r\nC\r\n";
        String inlineBody = "confText=" + urlEncode(inlineConf);
        String inlineUpload = send(port, "POST /create-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + inlineBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + inlineBody);
        check(inlineUpload.contains(">A+B=C<"), "inline text config can be deployed", "A+B=C",
                responseBody(inlineUpload));
        check(inlineUpload.contains("/publish?quiet=1"),
                "config deploy refreshes topic values without reloading the graph frame", "quiet topic refresh",
                responseBody(inlineUpload));

        String appendBody = "appendConfText=" + urlEncode("configs.IncAgent\r\nC\r\nD\r\n");
        String appendResponse = send(port, "POST /append-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + appendBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + appendBody);
        check(appendResponse.contains(">A+B=C<") && appendResponse.contains(">C+1=D<"),
                "agent blocks can be appended to the current config", "A+B=C and C+1=D",
                responseBody(appendResponse));

        String editBody = "action=removeAgent&agent=" + urlEncode("C+1=D");
        String editResponse = send(port, "POST /edit-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + editBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + editBody);
        check(editResponse.contains(">A+B=C<") && !editResponse.contains(">C+1=D<"),
                "agent nodes can be removed from the current config", "A+B=C without C+1=D",
                responseBody(editResponse));

        String addInputEdgeBody = "action=addEdge&from=E&to=" + urlEncode("A+B=C");
        String addInputEdgeResponse = send(port, "POST /edit-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + addInputEdgeBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + addInputEdgeBody);
        check(addInputEdgeResponse.contains(">A+B+E=C<") && addInputEdgeResponse.contains(">E<"),
                "input edges can be added to variable-input agents", "A+B+E=C", responseBody(addInputEdgeResponse));

        String addOutputEdgeBody = "action=addEdge&from=" + urlEncode("A+B+E=C") + "&to=F";
        String addOutputEdgeResponse = send(port, "POST /edit-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + addOutputEdgeBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + addOutputEdgeBody);
        check(addOutputEdgeResponse.contains(">A+B+E=C,F<") && addOutputEdgeResponse.contains(">F<"),
                "output edges can be added from agents to new output topics", "A+B+E=C,F",
                responseBody(addOutputEdgeResponse));

        String duplicateEdgeBody = "action=addEdge&from=" + urlEncode("A+B+E=C,F") + "&to=F";
        String duplicateEdgeResponse = send(port, "POST /edit-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + duplicateEdgeBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + duplicateEdgeBody);
        check(duplicateEdgeResponse.contains("Edge+already+exists") || duplicateEdgeResponse.contains("Edge already exists"),
                "duplicate edge additions are rejected", "Edge already exists", responseBody(duplicateEdgeResponse));

        String resetResponse = send(port, "POST /reset-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n");
        check(resetResponse.contains("No graph loaded.") && !resetResponse.contains(">A+B=C<"),
                "reset clears the active config and graph", "No graph loaded", responseBody(resetResponse));
        String topicsAfterReset = send(port, "GET /publish?quiet=1 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(!topicsAfterReset.contains("<td>A</td>") && !topicsAfterReset.contains("<td>C</td>"),
                "reset clears topic values and source topics", "no old topics", responseBody(topicsAfterReset));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void booleanAndMathFamiliesCanBeAdded() throws Exception {
        section("boolean and math prebuilt agent families");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String boolConf = "configs.AndAgent\nA,B\nY\n";
        String boolUpload = send(port, multipartRequest("/upload", "and.conf", boolConf));
        check(boolUpload.contains(">AND(A,B)=Y<") && boolUpload.contains("class=\"agent gate\"")
                && boolUpload.contains("class=\"node-label gate-label\""),
                "boolean gates render with gate-shaped nodes", "AND gate node", responseBody(boolUpload));

        String boolResult = send(port,
                "GET /publishAll?count=2&topic0=A&message0=1&topic1=B&message1=0 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(boolResult.contains("<td>Y</td><td>0</td>"), "AND gate computes boolean output", "Y=0",
                responseBody(boolResult));

        String invalidBool = send(port,
                "GET /publishAll?count=2&topic0=A&message0=1&topic1=B&message1=7 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(invalidBool.contains("<td>Y</td><td>invalid</td>"),
                "Boolean gate invalid input replaces the previous output", "Y=invalid",
                responseBody(invalidBool));

        String mathConf = "configs.MulAgent\nX,Z\nM\n";
        String mathUpload = send(port, multipartRequest("/upload", "mul.conf", mathConf));
        check(mathUpload.contains(">MUL(X,Z)=M<"), "math family agents can be loaded", "MUL(X,Z)=M",
                responseBody(mathUpload));

        String mathResult = send(port,
                "GET /publishAll?count=2&topic0=X&message0=6&topic1=Z&message1=7 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(mathResult.contains("<td>M</td><td>42.0</td>"), "MUL computes numeric output", "M=42.0",
                responseBody(mathResult));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void variableInputAgentsCanBeBuilt() throws Exception {
        section("variable-input agents");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String form = send(port, "GET /app/form.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(form.contains("id=\"addAgentInput\"") && form.contains("data-variable=\"true\""),
                "prebuilt agent form supports adding more inputs", "add input button", responseBody(form));
        check(form.contains("id=\"initMode\"") && form.contains("id=\"editMode\"")
                && form.contains("requires-edge"),
                "left panel groups initialization and edit tools by selected action", "mode selects",
                responseBody(form));

        String appendFirstBody = "appendConfText=" + urlEncode("configs.PlusAgent\r\nA,B,C,D\r\nSUM\r\n");
        String appendFirst = send(port, "POST /append-config HTTP/1.1\r\nHost: localhost\r\nContent-Length: "
                + appendFirstBody.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + appendFirstBody);
        check(appendFirst.contains(">A+B+C+D=SUM<"),
                "adding an agent can create the first config without a prior upload", "A+B+C+D=SUM",
                responseBody(appendFirst));

        String plusConf = "configs.PlusAgent\nA,B,C,D\nSUM\n";
        String plusUpload = send(port, multipartRequest("/upload", "plus4.conf", plusConf));
        check(plusUpload.contains(">A+B+C+D=SUM<"), "PlusAgent label includes every input", "A+B+C+D=SUM",
                responseBody(plusUpload));

        String plusResult = send(port,
                "GET /publishAll?count=4&topic0=A&message0=1&topic1=B&message1=2&topic2=C&message2=3&topic3=D&message3=4 HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(plusResult.contains("<td>SUM</td><td>10.0</td>"), "PlusAgent computes more than two inputs",
                "SUM=10.0", responseBody(plusResult));

        String andConf = "configs.AndAgent\nA,B,C,D\nALL_TRUE\n";
        String andUpload = send(port, multipartRequest("/upload", "and4.conf", andConf));
        check(andUpload.contains(">AND(A,B,C,D)=ALL_TRUE<") && andUpload.contains("class=\"agent gate\""),
                "Boolean gate label includes every input", "AND(A,B,C,D)=ALL_TRUE", responseBody(andUpload));

        String andResult = send(port,
                "GET /publishAll?count=4&topic0=A&message0=1&topic1=B&message1=true&topic2=C&message2=yes&topic3=D&message3=on HTTP/1.1\r\nHost: localhost\r\n\r\n");
        check(andResult.contains("<td>ALL_TRUE</td><td>1</td>"), "Boolean gates compute more than two inputs",
                "ALL_TRUE=1", responseBody(andResult));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void invalidUploadReturnsHtmlError() throws Exception {
        section("ConfLoader invalid upload");
        TopicManagerSingleton.get().clear();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String badConf = "configs.PlusAgent\nA,B\n";
        String response = send(port, multipartRequest("/upload", "bad.conf", badConf));
        check(!response.contains("Config upload failed") && response.contains("notice="),
                "invalid config errors are routed to topic values", "notice in right panel URL",
                responseBody(response));
        check(response.contains("/publish?quiet=1") && !response.contains("frames['center'].location='/graph'"),
                "invalid config errors do not overwrite center with an error page", "quiet refresh and no graph reload",
                responseBody(response));

        String duplicatePublisherConf = "configs.PlusAgent\nA,B\nE\nconfigs.PlusAgent\nC,D\nE\n";
        String duplicateResponse = send(port, multipartRequest("/upload", "duplicate-publisher.conf",
                duplicatePublisherConf));
        check(duplicateResponse.contains("notice=Topic+E+has+more+than+one+configured+publisher"),
                "config rejects a topic calculated by two different agents", "duplicate publisher rejected",
                responseBody(duplicateResponse));

        server.close();
        server.join(2000);
        TopicManagerSingleton.get().clear();
    }

    private static void parallelAgentCloseDoesNotBlockWhenQueueIsFull() throws Exception {
        section("ParallelAgent bounded shutdown");
        int before = countAssignmentThreads();
        BlockingAgent blockingAgent = new BlockingAgent();
        ParallelAgent parallelAgent = new ParallelAgent(blockingAgent, 1);

        parallelAgent.callback("A", new Message("first"));
        Thread.sleep(80);
        parallelAgent.callback("A", new Message("second"));

        long start = System.currentTimeMillis();
        parallelAgent.close();
        long elapsed = System.currentTimeMillis() - start;
        Thread.sleep(150);

        check(elapsed < 1500, "ParallelAgent close returns promptly when its queue is full", "< 1500ms",
                elapsed + "ms");
        check(countAssignmentThreads() == before, "ParallelAgent bounded close leaves no worker thread", before,
                countAssignmentThreads());
    }

    private static void serverCloseClosesConfigThreads() throws Exception {
        section("close cleans uploaded config threads");
        int before = countAssignmentThreads();
        int port = freePort();
        MyHTTPServer server = fullServer(port);
        server.start();
        Thread.sleep(150);

        String conf = "configs.PlusAgent\nA,B\nC\nconfigs.IncAgent\nC\nD\n";
        send(port, multipartRequest("/upload", "simple.conf", conf));
        server.close();
        server.join(2000);
        Thread.sleep(1200);
        int after = countAssignmentThreads();

        check(after == before, "server close leaves no HTTP or ParallelAgent threads", before, after);
        TopicManagerSingleton.get().clear();
    }

    private static MyHTTPServer fullServer(int port) {
        MyHTTPServer server = new MyHTTPServer(port, 5);
        ConfLoader confLoader = new ConfLoader();
        server.addServlet("GET", "/publish", new TopicDisplayer());
        server.addServlet("GET", "/publishAll", new BulkTopicDisplayer());
        server.addServlet("GET", "/topic-form", new TopicFormDisplayer());
        server.addServlet("GET", "/graph", new GraphDisplayer());
        server.addServlet("POST", "/upload", confLoader);
        server.addServlet("POST", "/create-config", confLoader);
        server.addServlet("POST", "/append-config", confLoader);
        server.addServlet("POST", "/edit-config", confLoader);
        server.addServlet("POST", "/reset-config", confLoader);
        server.addServlet("GET", "/app/", new HtmlLoader("html_files"));
        return server;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String multipartRequest(String uri, String fileName, String content) {
        String boundary = "----Assignment6Boundary";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"conf\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: text/plain\r\n\r\n"
                + content + "\r\n"
                + "--" + boundary + "--\r\n";
        return "POST " + uri + " HTTP/1.1\r\n"
                + "Host: localhost\r\n"
                + "Content-Type: multipart/form-data; boundary=" + boundary + "\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                + body;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String send(int port, String request) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String status(String response) {
        int end = response.indexOf("\r\n");
        return end >= 0 ? response.substring(0, end) : response;
    }

    private static String responseBody(String response) {
        int index = response.indexOf("\r\n\r\n");
        return index >= 0 ? response.substring(index + 4) : response;
    }

    private static int countAssignmentThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String name = thread.getName();
            if (thread.isAlive()
                    && (name.startsWith("MyHTTPServer-main-")
                            || name.startsWith("pool-")
                            || name.startsWith("ParallelAgent-"))) {
                count++;
            }
        }
        return count;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("TEST: " + title);
    }

    private static void check(boolean condition, String description, Object expected, Object actual) {
        if (!condition) {
            throw new AssertionError(description + " | expected: " + expected + ", actual: " + actual);
        }
        passed++;
        System.out.println("  PASS #" + passed + ": " + description
                + " | expected: " + expected + ", actual: " + actual);
    }

    private static class BlockingAgent implements Agent {
        @Override
        public String getName() {
            return "blocking-test";
        }

        @Override
        public void reset() {
        }

        @Override
        public void callback(String topic, Message msg) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
        }
    }
}
