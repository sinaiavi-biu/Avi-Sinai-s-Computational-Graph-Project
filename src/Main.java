import server.HTTPServer;
import server.MyHTTPServer;
import servlets.ConfLoader;
import servlets.BulkTopicDisplayer;
import servlets.GraphDisplayer;
import servlets.HtmlLoader;
import servlets.TopicDisplayer;
import servlets.TopicFormDisplayer;

public class Main {
    public static void main(String[] args) throws Exception {
        HTTPServer server = new MyHTTPServer(8080, 5);
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

        server.start();
        System.in.read();
        server.close();
        System.out.println("done");
    }
}
