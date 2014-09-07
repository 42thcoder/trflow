package org.jenkinsci.plugins.trflow.webhook;

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.model.*;
import hudson.model.Hudson.MasterComputer;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.util.SequentialExecutionQueue;
import net.sf.json.JSONObject;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Triggers a build when we receive a Jira event.
 *
 * @author Diego Casas
 */
public class JiraTrigger extends Trigger<AbstractProject<?, ?>> {
    private final String projectKey;

    @DataBoundConstructor
    public JiraTrigger(String projectKey) {
        this.projectKey = projectKey;
    }

    protected void scheduleBuild(final JiraEventCause cause) {
        getDescriptor().queue.execute(new Runnable() {
            public void run() {
                if (job.scheduleBuild(0, cause, cause.getParameters())) {
                    LOGGER.log(INFO, "Jira event received. Triggering job {0} #{1}", new Object[]{job.getName(), job.getNextBuildNumber()});
                } else {
                    LOGGER.log(INFO, "Jira event received. Job {0} is already in the queue.", job.getName());
                }
            }
        });
    }

    public String getProjectKey() {
        return projectKey;
    }

    /**
     * Returns the file that records the last/current polling activity.
     */
    public File getLogFile() {
        return new File(job.getRootDir(), "jira-polling.log");
    }

    @Override
    public void start(AbstractProject<?, ?> project, boolean newInstance) {
        super.start(project, newInstance);
        if (newInstance && getDescriptor().isManageHook()) {
            // make sure we have hooks installed. do this lazily to avoid blocking the UI thread.
            getDescriptor().queue.execute(new Runnable() {
                public void run() {
                    createJenkinsHook(null, projectKey);
                }
            });
        }
    }

    private boolean createJenkinsHook(URL url, String projectKey) {
        LOGGER.log(WARNING, "TODO: configure Jira webhooks automatically");
        return true;
    }

    @Override
    public void stop() {
        if (getDescriptor().isManageHook()) {
            LOGGER.log(WARNING, "TODO: delete Jira webhooks automatically");
        }
    }

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new GitHubWebHookPollingAction());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }



    /**
     * Action object for {@link Project}. Used to display the polling log.
     */
    public final class GitHubWebHookPollingAction implements Action {
        public AbstractProject<?, ?> getOwner() {
            return job;
        }

        public String getIconFileName() {
            return "clipboard.png";
        }

        public String getDisplayName() {
            return "Jira Hook Log";
        }

        public String getUrlName() {
            return "JiraPollLog";
        }

        public String getLog() throws IOException {
            return Util.loadFile(getLogFile());
        }

        /**
         * Writes the annotated log to the given output.
         *
         * @since 1.350
         */
        public void writeLogTo(XMLOutput out) throws IOException {
            new AnnotatedLargeText<GitHubWebHookPollingAction>(getLogFile(), Charset.defaultCharset(), true, this).writeHtmlTo(0, out.asWriter());
        }
    }

    @Extension
    public static class DescriptorImpl extends TriggerDescriptor {
        private transient final SequentialExecutionQueue queue = new SequentialExecutionQueue(MasterComputer.threadPoolForRemoting);

        private boolean manageHook;
        private String hookUrl;
        private volatile List<Credential> credentials = new ArrayList<Credential>();

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Item item) {
            return item instanceof AbstractProject;
        }

        @Override
        public String getDisplayName() {
            return "Build when an event is sent from Jira";
        }

        /**
         * True if Jenkins should auto-manage hooks.
         */
        public boolean isManageHook() {
            return manageHook;
        }

        public void setManageHook(boolean v) {
            manageHook = v;
            save();
        }

        /**
         * Returns the URL that Jira should post.
         */
        public URL getHookUrl() throws MalformedURLException {
            return hookUrl != null ? new URL(hookUrl) : new URL(Hudson.getInstance().getRootUrl() + JiraWebHook.get().getUrlName() + '/');
        }

        public boolean hasOverrideURL() {
            return hookUrl != null;
        }

        public List<Credential> getCredentials() {
            return credentials;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            JSONObject hookMode = json.getJSONObject("hookMode");
            manageHook = "auto".equals(hookMode.getString("value"));
            JSONObject o = hookMode.getJSONObject("hookUrl");
            if (o != null && !o.isNullObject()) {
                hookUrl = o.getString("url");
            } else {
                hookUrl = null;
            }
            credentials = req.bindJSONToList(Credential.class, hookMode.get("credentials"));
            save();
            return true;
        }

        public static DescriptorImpl get() {
            return Trigger.all().get(DescriptorImpl.class);
        }

        public static boolean allowsHookUrlOverride() {
            return ALLOW_HOOKURL_OVERRIDE;
        }
    }

    /**
     * Set to false to prevent the user from overriding the hook URL.
     */
    public static boolean ALLOW_HOOKURL_OVERRIDE = !Boolean.getBoolean(JiraTrigger.class.getName() + ".disableOverride");

    private static final Logger LOGGER = Logger.getLogger(JiraTrigger.class.getName());
}