package servlets;

import server.RequestParser.RequestInfo;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Request handler used by {@code MyHTTPServer}.
 *
 * <p>A servlet receives parsed request data and writes the full HTTP response
 * directly to the client output stream. Implementations should include the
 * status line, headers, a blank line, and the response body.</p>
 */
public interface Servlet {
    /**
     * Handles one HTTP request.
     *
     * @param ri parsed request information
     * @param toClient output stream connected to the client socket
     * @throws IOException if writing the response fails
     */
    void handle(RequestInfo ri, OutputStream toClient) throws IOException;

    /**
     * Releases resources owned by this servlet.
     *
     * @throws IOException if cleanup fails
     */
    void close() throws IOException;
}
