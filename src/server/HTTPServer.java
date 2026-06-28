package server;

import servlets.Servlet;

/**
 * Minimal HTTP server contract used by the project.
 *
 * <p>The server accepts servlet registrations by HTTP method and URI prefix.
 * Implementations are responsible for accepting socket connections, parsing
 * requests, dispatching to the longest matching servlet prefix, and closing
 * registered servlets during shutdown.</p>
 */
public interface HTTPServer extends Runnable {
    /**
     * Registers a servlet for the given HTTP method and URI prefix.
     *
     * @param httpCommand HTTP method such as {@code GET}, {@code POST}, or {@code DELETE}
     * @param uri URI prefix handled by the servlet, for example {@code /app/}
     * @param s servlet that writes the response
     */
    void addServlet(String httpCommand, String uri, Servlet s);

    /**
     * Removes a servlet registration.
     *
     * @param httpCommand HTTP method used during registration
     * @param uri URI prefix used during registration
     */
    void removeServlet(String httpCommand, String uri);

    /**
     * Starts the server accept loop on its background thread.
     */
    void start();

    /**
     * Stops accepting new clients, closes the server socket, shuts down worker
     * threads, and closes registered servlets.
     */
    void close();
}
