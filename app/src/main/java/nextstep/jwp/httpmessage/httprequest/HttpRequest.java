package nextstep.jwp.httpmessage.httprequest;

import nextstep.jwp.httpmessage.HttpHeaders;
import nextstep.jwp.httpmessage.HttpMethod;
import nextstep.jwp.httpmessage.HttpSession;
import nextstep.jwp.httpmessage.HttpSessions;

import java.util.Enumeration;
import java.util.Objects;
import java.util.UUID;

public class HttpRequest {

    private final RequestLine requestLine;
    private final HttpHeaders httpHeaders;
    private final Parameters parameters;

    private HttpSession httpSession;

    public HttpRequest(HttpMessageReader bufferedReader) {
        this(new RequestLine(bufferedReader.getStartLine()),
                new HttpHeaders(bufferedReader.getHeaders()),
                new Parameters(bufferedReader.getParameters())
        );
    }

    public HttpRequest(RequestLine requestLine, HttpHeaders httpHeaders, Parameters parameters) {
        this(requestLine, httpHeaders, parameters, HttpSessions.getSession(httpHeaders.getSessionId()));
    }

    public HttpRequest(RequestLine requestLine, HttpHeaders httpHeaders, Parameters parameters, HttpSession httpSession) {
        this.requestLine = requestLine;
        this.httpHeaders = httpHeaders;
        this.parameters = parameters;
        this.httpSession = initializeSession(httpSession);
    }

    public String getRequestLine() {
        return requestLine.getLine();
    }

    public HttpMethod getHttpMethod() {
        return requestLine.getMethod();
    }

    public String getPath() {
        return requestLine.getPath();
    }

    public String getVersionOfTheProtocol() {
        return requestLine.getVersionOfTheProtocol();
    }

    public String getHeader(String name) {
        return httpHeaders.getHeader(name);
    }

    public int httpHeaderSize() {
        return httpHeaders.size();
    }

    public Enumeration<String> getParameterNames() {
        return parameters.getParameterNames();
    }

    public String getParameter(String name) {
        return parameters.getParameter(name);
    }

    public HttpSession getHttpSession() {
        if (httpSession.isDefaultHttpSession()) {
            this.httpSession = new HttpSession(UUID.randomUUID().toString());
            return httpSession;
        }
        return httpSession;
    }

    public String getHttpSessionId() {
        return httpSession.getId();
    }

    private HttpSession initializeSession(HttpSession httpSession) {
        if (Objects.isNull(httpSession)) {
            return HttpSession.DEFAULT_HTTP_SESSION;
        }
        return httpSession;
    }

    public boolean hasDefaultSession() {
        return httpSession.isDefaultHttpSession();
    }
}
