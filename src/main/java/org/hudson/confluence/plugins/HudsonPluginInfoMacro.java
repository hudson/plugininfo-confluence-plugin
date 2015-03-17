package org.hudson.confluence.plugins;

import com.atlassian.confluence.json.parser.JSONException;
import com.atlassian.confluence.json.parser.JSONObject;
import com.atlassian.confluence.util.http.HttpResponse;
import com.atlassian.confluence.util.http.HttpRetrievalService;
import com.atlassian.renderer.RenderContext;
import com.atlassian.renderer.v2.RenderMode;
import com.atlassian.renderer.v2.SubRenderer;
import com.atlassian.renderer.v2.macro.BaseMacro;
import com.atlassian.renderer.v2.macro.MacroException;
import java.io.IOException;
import java.util.Map;
import org.apache.commons.io.IOUtils;

/**
 * A macro to fetch the hudson plugin information from the update center JSON
 * @author Winston Prakash
 */
public class HudsonPluginInfoMacro extends BaseMacro {

     private HttpRetrievalService httpRetrievalService;

    private static final String OUTPUT_PARAMETER = "output";
    private static final String OUTPUT_PARAMETER_HTML = "html";
    private static final String OUTPUT_PARAMETER_WIKI = "wiki";
    
    private static final String UPDATE_CENTER_JSON = "http://hudson-ci.org/update-center3.2/update-center.json";
    private static final String PLUGIN_CENTRAL_NAME = "Plugin Central 3.2";
    private static final String PLUGIN_CENTRAL_URL = "http://hudson-ci.org/PluginCentral/site/3.2/";
    
    private static final String CORE_DOWNLOAD_URL = "https://www.eclipse.org/hudson/download.php";

    public boolean isInline() {
        return false;
    }

    public boolean hasBody() {
        return false;
    }

    public RenderMode getBodyRenderMode() {
        return RenderMode.NO_RENDER;
    }

    /**
     * Setter method for automatic injection of the {@link HttpRetrievalService}.
     *
     * @param httpRetrievalService the http retrieval service to use
     */
    public void setHttpRetrievalService(HttpRetrievalService httpRetrievalService) {
        this.httpRetrievalService = httpRetrievalService;
    }

    private SubRenderer subRenderer;

    public void setSubRenderer(SubRenderer subRenderer) {
        this.subRenderer = subRenderer;
    }

    /**
     * This method returns XHTML to be displayed on the page that uses this macro
     */
    public String execute(Map parameters, String body, RenderContext renderContext)
            throws MacroException {

        String pluginId = (String) parameters.get("pluginId");

        if (pluginId == null) {
            pluginId = (String) parameters.get("0");
        }

        if (pluginId == null) {
            return "No plugin specified.";
        }

        String jiraComponent = (String) parameters.get("jiraComponent");

        String sourceDir = (String) parameters.get("sourceDir");

        try {
            HttpResponse response = httpRetrievalService.get(UPDATE_CENTER_JSON);
            if (response.getStatusCode() != 200) {
                return subRenderer.render("h4. Plugin Information\n"
                        + "{warning:title=Cannot Load Update Center}\n"
                        + "error " + response.getStatusCode() + " loading update-center.json\n"
                        + "{warning}\n", renderContext);
            }

            String rawUpdateCenter = IOUtils.toString(response.getResponse()).trim();

            if (rawUpdateCenter.startsWith("updateCenter.post(")) {
                rawUpdateCenter = rawUpdateCenter.substring(new String("updateCenter.post(").length());
            }

            if (rawUpdateCenter.endsWith(");")) {
                rawUpdateCenter = rawUpdateCenter.substring(0, rawUpdateCenter.lastIndexOf(");"));
            }

            JSONObject updateCenter;

            updateCenter = new JSONObject(rawUpdateCenter);

            StringBuilder toBeRendered = null;
            for (String pluginKey : JSONObject.getNames(updateCenter.getJSONObject("plugins"))) {
                if (pluginKey.equals(pluginId)) {
                    JSONObject pluginJSON = updateCenter.getJSONObject("plugins").getJSONObject(pluginKey);

                    String name = getString(pluginJSON, "name");

                    if (jiraComponent == null) {
                        jiraComponent = name;
                    }

                    if (sourceDir == null) {
                        sourceDir = name;
                    }

                    toBeRendered = new StringBuilder("h4. Plugin Information\n"
                            + "|| Plugin ID | " + name + " |\n"
                            + "|| Latest Release | " + getString(pluginJSON, "version") + " |\n"
                            + "|| Latest Release Date | " + getString(pluginJSON, "buildDate") + " |\n"
                            );
                    
                    toBeRendered.append("|| Plugin Central | [").append(PLUGIN_CENTRAL_NAME).append("| ").append(PLUGIN_CENTRAL_URL).append("] |\n");
                    
                    String scmURL = getString(pluginJSON, "scm");
                    String scmType = "External";
                    if (scmURL.contains("java.net")){
                        scmURL = "http://svn.java.net/svn/hudson~plugins/";
                        scmType = "Subversion";
                    }else if (scmURL.startsWith("github")){
                        scmURL = "https://github.com/hudson-plugins";
                        scmType = "Github";
                    }else if (scmURL.contains("github.com")){
                        scmType = "Github";
                    }
                    toBeRendered.append("|| Sources | [").append(scmType).append("| ").append(scmURL).append("] |\n");
                    toBeRendered.append("|| Support | [Eclipse Hudson Forum | http://www.eclipse.org/forums/index.php?t=thread&frm_id=229] |\n");
                    toBeRendered.append("|| Issue Tracking | [Eclipse Bugzilla | https://bugs.eclipse.org/bugs/enter_bug.cgi?product=Hudson] |\n");
                    
                    String coreVersion = getString(updateCenter.getJSONObject("core"), "version");
                            
                    toBeRendered.append("|| Hudson Core (latest) | [").append(coreVersion).append("| ").append(CORE_DOWNLOAD_URL).append("] |\n");
                }
            }

            if (toBeRendered == null) {
                toBeRendered = new StringBuilder("h4. Plugin Information\n");
                toBeRendered.append("|| No Information For This Plugin ||\n");
            }

            return subRenderer.render(toBeRendered.toString(), renderContext);
        } catch (JSONException e) {
            return subRenderer.render("h4. Plugin Information\n"
                    + "{warning:title=Cannot Load Update Center}\n"
                    + "JSONException: " + e.getMessage() + "\n"
                    + "{warning}\n", renderContext);
        } catch (IOException e) {
            return subRenderer.render("h4. Plugin Information\n"
                    + "{warning:title=Cannot Load Update Center}\n"
                    + "IOException: " + e.getMessage() + "\n"
                    + "{warning}\n", renderContext);
        }
    }

    private String getString(JSONObject o, String prop) throws JSONException {
        if(o.has(prop))
            return o.getString(prop);
        else
            return "n/a";
    }
}
