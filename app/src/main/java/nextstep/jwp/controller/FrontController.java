package nextstep.jwp.controller;

import nextstep.jwp.controller.modelview.ModelView;
import nextstep.jwp.controller.modelview.View;
import nextstep.jwp.httpmessage.ContentType;
import nextstep.jwp.httpmessage.httprequest.HttpRequest;
import nextstep.jwp.httpmessage.httpresponse.HttpResponse;
import nextstep.jwp.httpmessage.httpresponse.HttpStatusCode;

import java.util.HashMap;
import java.util.Map;

public class FrontController {

    private final Map<String, Controller> controllerMap = new HashMap<>();

    public FrontController() {
        initHandlerMapping();
    }

    public void service(HttpRequest httpRequest, HttpResponse httpResponse) {
        final String requestPath = httpRequest.getPath();
        final Controller controller = controllerMap.getOrDefault(requestPath, new RedirectController());

        try {
            if (ContentType.containValueByUrl(requestPath)) {
                View view = new View(requestPath);
                view.render(httpRequest, httpResponse);
                return;
            }
            ModelView modelView = controller.process(httpRequest, httpResponse);
            View view = new View(modelView.getViewName());
            view.render(httpRequest, httpResponse);
        } catch (UnsupportedOperationException exception) {
            httpResponse.setHttpStatusCode(HttpStatusCode.NOT_FOUND);
            errorResolver(httpRequest, httpResponse);
        } catch (RuntimeException exception) {
            httpResponse.setHttpStatusCode(HttpStatusCode.UNAUTHORIZED);
            errorResolver(httpRequest, httpResponse);
        }
    }

    public void errorResolver(HttpRequest httpRequest, HttpResponse httpResponse) {
        View view = new View("/" + httpResponse.getHttpStatusCode().getValue() + ".html");
        httpResponse.setHttpStatusCode(HttpStatusCode.FOUND);
        view.render(httpRequest, httpResponse);
    }

    private void initHandlerMapping() {
        controllerMap.put("/", new HelloController());
        controllerMap.put("/login", new UserLoginController());
        controllerMap.put("/register", new UserRegisterController());
    }
}
