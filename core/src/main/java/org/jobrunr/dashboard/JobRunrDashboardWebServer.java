package org.jobrunr.dashboard;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import org.jobrunr.dashboard.server.TeenyHttpHandler;
import org.jobrunr.dashboard.server.TeenyWebServer;
import org.jobrunr.dashboard.server.http.RedirectHttpHandler;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.utils.annotations.VisibleFor;
import org.jobrunr.utils.mapper.JsonMapper;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration;
import static org.jobrunr.utils.StringUtils.isNotNullOrEmpty;

/**
 * Provides a dashboard which gives insights in your jobs and servers.
 * The dashboard server starts by default on port 8000.
 *
 * @author Ronald Dehuysser
 */
public class JobRunrDashboardWebServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRunrDashboardWebServer.class);

    private final StorageProvider storageProvider;
    private final JsonMapper jsonMapper;
    private final int port;
    private final BasicAuthenticator basicAuthenticator;

    private TeenyWebServer teenyWebServer;

    public static void main(String[] args) {
        new JobRunrDashboardWebServer(null, new JacksonJsonMapper());
    }

    public JobRunrDashboardWebServer(StorageProvider storageProvider, JsonMapper jsonMapper) {
        this(storageProvider, jsonMapper, 8000);
    }

    public JobRunrDashboardWebServer(StorageProvider storageProvider, JsonMapper jsonMapper, int port) {
        this(storageProvider, jsonMapper, usingStandardDashboardConfiguration().andPort(port));
    }

    public JobRunrDashboardWebServer(StorageProvider storageProvider, JsonMapper jsonMapper, int port, String username, String password) {
        this(storageProvider, jsonMapper, usingStandardDashboardConfiguration().andPort(port).andBasicAuthentication(username, password));
    }

    public JobRunrDashboardWebServer(StorageProvider storageProvider, JsonMapper jsonMapper, JobRunrDashboardWebServerConfiguration configuration) {
        this.storageProvider = new ThreadSafeStorageProvider(storageProvider);
        this.jsonMapper = jsonMapper;
        this.port = configuration.port;
        this.basicAuthenticator = createOptionalBasicAuthenticator(configuration.username, configuration.password);
    }

    public void start() {
        RedirectHttpHandler redirectHttpHandler = new RedirectHttpHandler("/", "/dashboard");
        JobRunrStaticFileHandler staticFileHandler = createStaticFileHandler();
        JobRunrApiHandler dashboardHandler = createApiHandler(storageProvider, jsonMapper);
        JobRunrSseHandler sseHandler = createSSeHandler(storageProvider, jsonMapper);

        teenyWebServer = new TeenyWebServer(port);
        registerContext(redirectHttpHandler);
        registerSecuredContext(staticFileHandler);
        registerSecuredContext(dashboardHandler);
        registerSecuredContext(sseHandler);
        teenyWebServer.start();

        LOGGER.info("JobRunr Dashboard started at http://{}:{}/dashboard",
                teenyWebServer.getWebServerHostAddress(),
                teenyWebServer.getWebServerHostPort());
    }

    public void stop() {
        if (teenyWebServer == null) return;
        teenyWebServer.stop();
        LOGGER.info("JobRunr dashboard stopped");
        teenyWebServer = null;
    }

    HttpContext registerContext(TeenyHttpHandler httpHandler) {
        return teenyWebServer.createContext(httpHandler);
    }

    HttpContext registerSecuredContext(TeenyHttpHandler httpHandler) {
        HttpContext httpContext = registerContext(httpHandler);
        if (basicAuthenticator != null) {
            httpContext.setAuthenticator(basicAuthenticator);
        }
        return httpContext;
    }

    @VisibleFor("github issue 18")
    JobRunrStaticFileHandler createStaticFileHandler() {
        return new JobRunrStaticFileHandler();
    }

    @VisibleFor("github issue 18")
    JobRunrApiHandler createApiHandler(StorageProvider storageProvider, JsonMapper jsonMapper) {
        return new JobRunrApiHandler(storageProvider, jsonMapper);
    }

    @VisibleFor("github issue 18")
    JobRunrSseHandler createSSeHandler(StorageProvider storageProvider, JsonMapper jsonMapper) {
        return new JobRunrSseHandler(storageProvider, jsonMapper);
    }

    private BasicAuthenticator createOptionalBasicAuthenticator(String username, String password) {
        if (isNotNullOrEmpty(username) && isNotNullOrEmpty(password)) {
            return new BasicAuthenticator("JobRunr") {
                @Override
                public boolean checkCredentials(String user, String pwd) {
                    return user.equals(username) && pwd.equals(password);
                }
            };
        }
        return null;
    }
}
