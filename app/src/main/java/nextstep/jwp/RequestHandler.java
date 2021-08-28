package nextstep.jwp;

import nextstep.jwp.db.InMemoryUserRepository;
import nextstep.jwp.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RequestHandler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(RequestHandler.class);
    private static final String DEFAULT_RESPONSE_BODY = "Hello world!";
    private static final String STATIC_PATH = "static";
    private static final String HEADER_DELIMITER = ": ";

    private static long userId = 2;

    private final Socket connection;

    public RequestHandler(Socket connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    @Override
    public void run() {
        LOG.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(), connection.getPort());

        try (final InputStream inputStream = connection.getInputStream();
             final OutputStream outputStream = connection.getOutputStream()) {
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            final String firstLine = bufferedReader.readLine();
            final Map<String, String> httpRequestHeaders = httpRequestHeaders(bufferedReader);
            final String extractedUri = extractUri(firstLine);
            final String extractedMethod = extractHttpMethod(firstLine);

            if ("/".equals(extractedUri)) {
                final String response = http200Message(DEFAULT_RESPONSE_BODY);
                writeOutputStream(outputStream, response);
                return;
            }

            if ("/login".equals(extractedUri)) {
                if ("POST".equals(extractedMethod)) {
                    try {
                        final String requestBody = extractRequestBody(bufferedReader, httpRequestHeaders);
                        loginRequest(requestBody);
                        writeOutputStream(outputStream, http302Response("/index.html"));
                    } catch (RuntimeException exception) {
                        writeOutputStream(outputStream, http302Response("/401.html"));
                    }
                    return;
                }
                writeOutputStream(outputStream, httpHtmlResponse(STATIC_PATH, extractedUri));
                return;
            }

            if ("/register".equals(extractedUri)) {
                if ("POST".equals(extractedMethod)) {
                    final String requestBody = extractRequestBody(bufferedReader, httpRequestHeaders);
                    registerRequest(requestBody);
                    writeOutputStream(outputStream, http302Response("/index.html"));
                    return;
                }
                writeOutputStream(outputStream, httpHtmlResponse(STATIC_PATH, extractedUri));
                return;
            }

            if ("/css/styles.css".equals(extractedUri)) {
                writeOutputStream(outputStream, httpCssResponse(STATIC_PATH, extractedUri));
                return;
            }

            writeOutputStream(outputStream, httpHtmlResponse(STATIC_PATH, extractedUri));
        } catch (IOException exception) {
            LOG.error("Exception stream", exception);
        } finally {
            close();
        }
    }

    private Map<String, String> httpRequestHeaders(BufferedReader bufferedReader) throws IOException {
        Map<String, String> httpRequestHeaders = new HashMap<>();
        while (bufferedReader.ready()) {
            final String line = bufferedReader.readLine();
            if (Objects.isNull(line)) {
                break;
            }
            if ("".equals(line)) {
                break;
            }
            final String[] header = line.split(HEADER_DELIMITER);
            httpRequestHeaders.put(header[0], header[1].strip());
        }
        return httpRequestHeaders;
    }

    private String extractUri(String firstLine) {
        final String[] splitFirstLine = firstLine.split(" ");
        return splitFirstLine[1];
    }

    private String extractHttpMethod(String firstLine) {
        final String[] splitFirstLine = firstLine.split(" ");
        return splitFirstLine[0];
    }

    private String extractRequestBody(BufferedReader bufferedReader, Map<String, String> httpRequestHeaders) throws IOException {
        final int contentLength = Integer.parseInt(httpRequestHeaders.get("Content-Length"));
        final char[] buffer = new char[contentLength];
        bufferedReader.read(buffer, 0, contentLength);
        return new String(buffer);
    }

    private String loginRequest(String requestBody) {
        final Map<String, String> params = extractQueryParam(requestBody);
        final User user = findUserByAccount(params);
        if (user.checkPassword(params.get("password"))) {
            return user.toString();
        }
        throw new IllegalArgumentException("옳지 않은 비밀번호입니다.");
    }

    private User findUserByAccount(Map<String, String> params) {
        final Optional<User> user = InMemoryUserRepository.findByAccount(params.get("account"));
        if (user.isPresent()) {
            return user.get();
        }
        throw new IllegalArgumentException("찾을 수 없는 사용자입니다.");
    }

    private void registerRequest(String requestBody) {
        final Map<String, String> params = extractQueryParam(requestBody);
        final User user = new User(userId++, params.get("account"), params.get("password"), params.get("email"));
        InMemoryUserRepository.save(user);
    }

    private Map<String, String> extractQueryParam(String queryParameterString) {
        final Map<String, String> params = new HashMap<>();
        final String[] parameters = queryParameterString.split("&");
        for (String parameter : parameters) {
            final String[] splitParameter = parameter.split("=");
            params.put(splitParameter[0], splitParameter[1]);
        }
        return params;
    }

    private void writeOutputStream(OutputStream outputStream, String response) throws IOException {
        outputStream.write(response.getBytes());
        outputStream.flush();
    }

    private String http200Message(String responseBody) {
        return String.join("\r\n",
                "HTTP/1.1 200 OK ",
                "Content-Type: text/html;charset=utf-8 ",
                "Content-Length: " + responseBody.getBytes().length + " ",
                "",
                responseBody);
    }

    private String httpCssMessage(String responseBody) {
        return String.join("\r\n",
                "HTTP/1.1 200 OK ",
                "Content-Type: text/css ",
                "Content-Length: " + responseBody.getBytes().length + " ",
                "",
                responseBody);
    }

    private String http302Response(String redirectUrl) {
        return String.join("\r\n",
                "HTTP/1.1 302 Found ",
                "Location: http://localhost:8080" + redirectUrl);
    }

    private String httpHtmlResponse(String resourcesPath, String targetUri) {
        if (!targetUri.contains(".") && !targetUri.contains(".html")) {
            targetUri = targetUri.concat(".html");
        }
        return http200Message(responseBodyByURLPath(resourcesPath, targetUri));
    }

    private String httpCssResponse(String resourcesPath, String targetUri) {
        return httpCssMessage(responseBodyByURLPath(resourcesPath, targetUri));
    }

    private String responseBodyByURLPath(String resourcesPath, String targetUri) {
        try {
            final URL url = getClass().getClassLoader().getResource(resourcesPath + targetUri);
            return Files.readString(new File(url.getFile()).toPath());
        } catch (IOException exception) {
            LOG.info("리소스를 가져오려는 url이 존재하지 않습니다. 입력값: {}", resourcesPath + targetUri);
            throw new IllegalStateException(String.format("리소스를 가져오려는 url이 존재하지 않습니다. 입력값: %s",
                    resourcesPath + targetUri));
        }
    }

    private void close() {
        try {
            connection.close();
        } catch (IOException exception) {
            LOG.error("Exception closing socket", exception);
        }
    }
}
