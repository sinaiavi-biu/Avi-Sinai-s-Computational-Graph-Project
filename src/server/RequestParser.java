package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the small HTTP request subset used by {@link MyHTTPServer}.
 *
 * <p>The parser reads the request line, headers, query parameters, URI path
 * segments, and an optional body controlled by {@code Content-Length}. It is
 * intentionally compact and dependency-free so the HTTP server can be reused
 * as a lightweight teaching/demo component.</p>
 */
public class RequestParser {
    private RequestParser() {
    }

    /**
     * Parses one HTTP request from a buffered character stream.
     *
     * @param reader source positioned at the start of an HTTP request
     * @return immutable request information for servlet dispatch
     * @throws IOException if the request line or content length is invalid
     */
    public static RequestInfo parseRequest(BufferedReader reader) throws IOException {
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.trim().isEmpty()) {
            throw new IOException("Empty HTTP request");
        }

        String[] requestParts = requestLine.trim().split("\\s+");
        if (requestParts.length < 2) {
            throw new IOException("Invalid HTTP request line: " + requestLine);
        }

        String command = requestParts[0].toUpperCase();
        String uri = requestParts[1];
        Map<String, String> headers = readHeaders(reader);
        int contentLength = parseContentLength(headers);
        byte[] content = readContent(reader, contentLength);

        String path = pathOnly(uri);
        String[] uriParts = uriParts(path);
        Map<String, String> parameters = parameters(uri);

        return new RequestInfo(command, uri, uriParts, parameters, content);
    }

    private static Map<String, String> readHeaders(BufferedReader reader) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                String key = line.substring(0, separator).trim().toLowerCase();
                String value = line.substring(separator + 1).trim();
                headers.put(key, value);
            }
        }
        return headers;
    }

    private static int parseContentLength(Map<String, String> headers) throws IOException {
        String value = headers.get("content-length");
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            int length = Integer.parseInt(value);
            if (length < 0) {
                throw new IOException("Negative Content-Length");
            }
            return length;
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Content-Length: " + value, e);
        }
    }

    private static byte[] readContent(BufferedReader reader, int contentLength) throws IOException {
        if (contentLength == 0) {
            return new byte[0];
        }
        // The project sends form and multipart bodies as UTF-8 text, so the
        // parser keeps the simple reader-based contract used by the server.
        char[] body = new char[contentLength];
        int offset = 0;
        while (offset < contentLength) {
            int read = reader.read(body, offset, contentLength - offset);
            if (read == -1) {
                break;
            }
            offset += read;
        }
        return new String(body, 0, offset).getBytes(StandardCharsets.UTF_8);
    }

    private static String pathOnly(String uri) {
        int queryIndex = uri.indexOf('?');
        return queryIndex >= 0 ? uri.substring(0, queryIndex) : uri;
    }

    private static String[] uriParts(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isEmpty()) {
            return new String[0];
        }
        return normalized.split("/");
    }

    private static Map<String, String> parameters(String uri) {
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0 || queryIndex == uri.length() - 1) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        String query = uri.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Parsed request data passed from the HTTP server to servlets.
     */
    public static class RequestInfo {
        /** Normalized HTTP method such as {@code GET} or {@code POST}. */
        public final String httpCommand;

        /** Original request URI, including query string when present. */
        public final String uri;

        /** URI path segments without leading slash or query string. */
        public final String[] uriParts;

        /** Decoded query parameters keyed by parameter name. */
        public final Map<String, String> parameters;

        /** Raw request body bytes. */
        public final byte[] content;

        /**
         * Creates a parsed request value.
         *
         * @param httpCommand normalized HTTP method
         * @param uri original request URI, including query string when present
         * @param uriParts path segments without the query string
         * @param parameters decoded query parameters
         * @param content raw request body bytes
         */
        public RequestInfo(String httpCommand, String uri, String[] uriParts, Map<String, String> parameters,
                byte[] content) {
            this.httpCommand = httpCommand;
            this.uri = uri;
            this.uriParts = uriParts;
            this.parameters = parameters;
            this.content = content;
        }

        /**
         * Gets the normalized HTTP method.
         *
         * @return normalized HTTP method
         */
        public String getHttpCommand() {
            return httpCommand;
        }

        /**
         * Gets the original request URI.
         *
         * @return original request URI
         */
        public String getUri() {
            return uri;
        }

        /**
         * Gets the URI path segments.
         *
         * @return URI path segments without leading slash or query string
         */
        public String[] getUriParts() {
            return uriParts;
        }

        /**
         * Gets the decoded query parameters.
         *
         * @return decoded query parameters
         */
        public Map<String, String> getParameters() {
            return parameters;
        }

        /**
         * Gets the raw request body.
         *
         * @return raw request body bytes
         */
        public byte[] getContent() {
            return content;
        }
    }
}
