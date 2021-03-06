package org.apereo.portlet.soffit.renderer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apereo.portlet.soffit.model.v1_0.Payload;
import org.apereo.portlet.soffit.model.v1_0.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
@RequestMapping("/soffit")
public class SoffitRendererController {

    /**
     * Name of HTTP header sent by the {@link SoffitConnectorController} to
     * signal which POJO the JSON payload my be deserialized into.  This is a
     * strategy for versioning and backwards compatibility.  The receiver of an
     * older payload is free to transform it to a newer one, if a newer one is
     * available (and that's the tactic we'll likely emply when it comes to it).
     */
    public static final String PAYLOAD_CLASS_HEADER = "X-Soffit-PayloadClass";

    public static final String CACHE_CONTROL_HEADER = "Cache-Control";

    /**
     * The default value for the <code>Cache-Control</code> header is "no-cache,"
     * which indicates the response should not be cached (until we later
     * implement ETag-based caching).  This header value will be sent if the
     * Soffit does not specify a value for scope or max-age  (they both must be
     * specified).
     */
    public static final String CACHE_CONTROL_NOCACHE = "no-cache";

    /**
     * Prefix for all custom properties.
     */
    public static final String PROPERTY_PREFIX = "soffit.";

    /**
     * Used to create a property key specific to the soffit for cache scope.
     */
    public static final String CACHE_SCOPE_PROPERTY_FORMAT = PROPERTY_PREFIX + "%s.cache.scope";

    /**
     * Used to create a property key specific to the soffit for cache max-age.
     */
    public static final String CACHE_MAXAGE_PROPERTY_FORMAT = PROPERTY_PREFIX + "%s.cache.max-age";

    private static final String MODEL_NAME = "soffit";

    @Autowired
    private Environment environment;

    @Value("${soffit.renderer.viewsLocation:/WEB-INF/soffit/}")
    private String viewsLocation;
    private final Map<ViewTuple,String> availableViews = new HashMap<>();

    final ObjectMapper objectMapper = new ObjectMapper();

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @RequestMapping(value="/{module}", method=RequestMethod.POST)
    public ModelAndView render(final HttpServletRequest req, final HttpServletResponse res,
            final @PathVariable String module, final @RequestBody String soffitJson) {

        logger.debug("Rendering for request URI '{}', soffitJson={}", req.getRequestURI(), soffitJson);

        String payloadClassName = null;
        try {

            payloadClassName = req.getHeader(PAYLOAD_CLASS_HEADER);
            if (payloadClassName == null) {
                final String msg = "HTTP Header '" + PAYLOAD_CLASS_HEADER + "' not specified";
                throw new IllegalArgumentException(msg);
            }
            final Class<?> payloadClass = Class.forName(payloadClassName);
            logger.debug("Selected payloadClass '{}' for request URI '{}'", payloadClass, req.getRequestURI());

            // Deserialize the payload
            final Object soffit = objectMapper.readValue(soffitJson, payloadClass);

            // Select a view
            final String viewName = selectView(req, module, soffit);

            // Set up cache headers appropriately
            configureCacheHeaders(res, module);

            return new ModelAndView(viewName.toString(), MODEL_NAME, soffit);

        } catch (IOException e) {
            final String msg = "Request body was not JSON or was not a valid SoffitRequest";
            throw new IllegalArgumentException(msg, e);
        } catch (ClassNotFoundException e) {
            final String msg = "Unable to locate the specified PayloadClass:  " + payloadClassName;
            throw new IllegalArgumentException(msg, e);
        }

    }

    /*
     * Implementation
     */

    private void configureCacheHeaders(final HttpServletResponse res, final String module) {

        final String cacheScopeProperty = String.format(CACHE_SCOPE_PROPERTY_FORMAT, module);
        final String cacheScopeValue = environment.getProperty(cacheScopeProperty);
        logger.debug("Selecting cacheScopeValue='{}' for property '{}'", cacheScopeValue, cacheScopeProperty);

        final String cacheMaxAgeProperty = String.format(CACHE_MAXAGE_PROPERTY_FORMAT, module);
        final String cacheMaxAgeValue = environment.getProperty(cacheMaxAgeProperty);
        logger.debug("Selecting cacheMaxAgeValue='{}' for property '{}'", cacheMaxAgeValue, cacheMaxAgeProperty);

        // Both must be specified, else we just use the default...
        final String cacheControl = (StringUtils.isNotEmpty(cacheScopeValue) && StringUtils.isNotEmpty(cacheMaxAgeValue))
                ? cacheScopeValue + ", max-age=" + cacheMaxAgeValue
                : CACHE_CONTROL_NOCACHE;
        logger.debug("Setting cache-control='{}' for module '{}'", cacheControl, module);

        res.setHeader(CACHE_CONTROL_HEADER, cacheControl);

    }

    private String selectView(final HttpServletRequest req, final String module, final Object payload) {

        /*
         * NOTE: In the future, when we actually have more than one possible
         * payloadClass, we will need to do better than simply hard-casting the
         * payload to the type we need.
         */
        final Payload soffit = (Payload) payload;

        final StringBuilder modulePathBuilder = new StringBuilder().append(viewsLocation);
        if (!viewsLocation.endsWith("/")) {
            // Final slash in the configs is optional
            modulePathBuilder.append("/");
        }
        modulePathBuilder.append(module).append("/");
        final String modulePath = modulePathBuilder.toString();

        logger.debug("Calculated modulePath of '{}'", modulePath);

        @SuppressWarnings("unchecked")
        final Set<String> moduleResources = req.getSession().getServletContext().getResourcePaths(modulePath);

        // Need to make a selection based on 3 things:  module (above), mode, & windowState
        final String modeLowercase = soffit.getRequest().getAttributes().get(Request.MODE).get(0).toLowerCase();
        final String windowStateLowercase = soffit.getRequest().getWindowState().toLowerCase();

        final ViewTuple viewTuple = new ViewTuple(modulePath, modeLowercase, windowStateLowercase);
        String rslt = availableViews.get(viewTuple);
        if (rslt == null) {
            /*
             * This circumstance means that we haven't looked (yet);
             * check for a file named to match all 3.
             */
            final String pathBasedOnModeAndState = getCompletePathforParts(modulePath, modeLowercase, windowStateLowercase);
            if (moduleResources.contains(pathBasedOnModeAndState)) {
                // We have a winner!
                availableViews.put(viewTuple, pathBasedOnModeAndState);
                rslt = pathBasedOnModeAndState;
            } else {
                // Widen the search (within this module) based on PortletMode only
                final String pathBasedOnModeOnly = getCompletePathforParts(modulePath, modeLowercase);
                if (moduleResources.contains(pathBasedOnModeOnly)) {
                    // We still need to store the choice so we're not constantly looking
                    availableViews.put(viewTuple, pathBasedOnModeOnly);
                    rslt = pathBasedOnModeOnly;
                } else {
                    throw new IllegalStateException("Unable to select a view for PortletMode="
                            + modeLowercase + " and WindowState=" + soffit.getRequest().getWindowState());
                }
            }
        }

        logger.info("Selected viewName='{}' for PortletMode='{}' and WindowState='{}'",
                                rslt, modeLowercase, soffit.getRequest().getWindowState());

        return rslt;

    }

    private String getCompletePathforParts(final String... parts) {

        StringBuilder rslt = new StringBuilder();

        for (String part : parts) {
            rslt.append(part);
            if (!part.endsWith("/")) {
                // First part will be a directory
                rslt.append(".");
            }
        }

        rslt.append("jsp");  // TODO:  support more options

        logger.debug("Calculated path '{}' for parts={}", rslt, parts);

        return rslt.toString();

    }

    /*
     * Nested Types
     */

    private static final class ViewTuple {

        private final String moduleName;
        private final String mode;
        private final String windowState;

        public ViewTuple(String moduleName, String mode, String windowState) {
            this.moduleName = moduleName;
            this.mode = mode;
            this.windowState = windowState;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mode == null) ? 0 : mode.hashCode());
            result = prime * result + ((moduleName == null) ? 0 : moduleName.hashCode());
            result = prime * result + ((windowState == null) ? 0 : windowState.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ViewTuple other = (ViewTuple) obj;
            if (mode == null) {
                if (other.mode != null)
                    return false;
            } else if (!mode.equals(other.mode))
                return false;
            if (moduleName == null) {
                if (other.moduleName != null)
                    return false;
            } else if (!moduleName.equals(other.moduleName))
                return false;
            if (windowState == null) {
                if (other.windowState != null)
                    return false;
            } else if (!windowState.equals(other.windowState))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "ViewTuple [moduleName=" + moduleName + ", mode=" + mode + ", windowState=" + windowState + "]";
        }

    }

}
