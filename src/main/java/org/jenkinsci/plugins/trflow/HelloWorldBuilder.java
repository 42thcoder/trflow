package org.jenkinsci.plugins.trflow;

import com.google.common.collect.Collections2;
import hudson.Extension;
import hudson.Launcher;
import hudson.maven.MavenModuleSet;
import hudson.model.*;
import hudson.plugins.git.*;
import hudson.plugins.jira.JiraSession;
import hudson.plugins.jira.JiraSite;
import hudson.plugins.nested_view.NestedView;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.trflow.domain.predicate.ByBranchName;
import org.jenkinsci.plugins.trflow.jenkins.CreateMavenJenkinsJobScmHook;
import org.jenkinsci.plugins.trflow.jenkins.DeleteMavenJenkinsJobScmHook;
import org.jenkinsci.plugins.trflow.scm.ScmBaseHook;
import org.jenkinsci.plugins.trflow.scm.git.TRGit;
import org.jenkinsci.plugins.trflow.webhook.JiraEventType;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import javax.xml.rpc.ServiceException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static java.util.Arrays.asList;
import static org.jenkinsci.plugins.trflow.webhook.JiraEventType.IssueCreated;
import static org.jenkinsci.plugins.trflow.webhook.JiraEventType.IssueDeleted;
import static org.jenkinsci.plugins.trflow.webhook.JiraEventType.IssueUpdated;
import static org.kohsuke.github.GHIssueState.OPEN;

/**
 * Sample {@link Builder}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link HelloWorldBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #branchName})
 * to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class HelloWorldBuilder extends Builder {
    private static final Hudson HUDSON = Hudson.getInstance();
    private final String branchName;
    private final String templateJobName;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public HelloWorldBuilder(String branchName, String templateJobName) {
        this.branchName = branchName;
        this.templateJobName = templateJobName;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getBranchName() {
        return branchName;
    }

    public String getTemplateJobName() {
        return templateJobName;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        final Map<String, String> vars = build.getBuildVariables();
        Properties p = new Properties();
        p.putAll(vars);
        p.list(listener.getLogger());

        final TRGit git = new TRGit(build, listener, "https://api.github.com", "dcasas", "3d8e307bd9d0a8f179298d35fd6fdd5f2d57b194");
        final JiraSite site = JiraSite.get(build.getProject());


        JiraEventType eventType = JiraEventType.valueOf(vars.get("jira.event.type"));
        String issueStatus = vars.get("jira.issue.status");
        String issuePrjKey = vars.get("jira.project.key");
        String issueType = vars.get("jira.issue.type");
        String issueKey = vars.get("jira.issue.key");
        String branch = issueType + "/" + issueKey;
        String viewName = branch.replace('/', '-');
        String project = git.getRepositoryName().toUpperCase();

        if (IssueUpdated.equals(eventType) && "In Review".equals(issueStatus)) {
            String head = issueType + "/" + issueKey;
            try {
                git.releaseBranch(head, site.getIssue(issueKey).title);
                return true;
            } catch (ServiceException e) {
                e.printStackTrace(listener.getLogger());
                return false;
            }
        }

        final NestedView projectView = getProjectView(project);
        final boolean beanchViewCreated = existsBranchView(branch, projectView);
        if ((IssueCreated.equals(eventType) || IssueUpdated.equals(eventType)) && !"Done".equals(issueStatus) && beanchViewCreated) {
            listener.getLogger().println("job '" + branch + "' already exists. Nothing to do.");
            return true;
        } else if ("Done".equals(issueStatus) && !beanchViewCreated) {
            listener.getLogger().println("job '" + branch + "' already deleted. Nothing to do.");
            return true;
        } else if ("Done".equals(issueStatus))
            eventType = IssueDeleted; // hack!


        switch (eventType) {
            case IssueCreated:
            case IssueUpdated:
                createBranchView(viewName, projectView);
                git.createBranch(branch, new CreateMavenJenkinsJobScmHook(listener, templateJobName));
                addComment(listener, site, issueKey, "Jenkins jobs created/updated for branch "+branch+" ("+HUDSON.getRootUrl()+"view/"+project+"/view/"+branch+")");
                break;
            case IssueDeleted:
                deleteBranchView(viewName, projectView);
                git.deleteBranch(branch, new DeleteMavenJenkinsJobScmHook(listener));
                break;
            default:
                listener.getLogger().println("Jira Event type '"+eventType+"' not supported");
                return false;
        }

        // TODO: attach build log to jira ticket ??

        return true;
    }

    private void addComment(BuildListener listener, JiraSite site, String issueKey, String comment) throws IOException {
        try {
            final JiraSession jira = site.getSession();
            jira.addCommentWithoutConstrains(issueKey, comment);
        } catch (ServiceException e) {
            e.printStackTrace(listener.getLogger());
            throw new RuntimeException(e);
        }
    }

    private void deleteBranchView(String branch, NestedView projectView) throws IOException {
        ListView branchView = (ListView)HUDSON.getView(branch);
        if (branchView != null) {
            projectView.deleteView(branchView);
            projectView.save();
        }
    }

    private boolean existsBranchView(String branch, NestedView projectView) throws IOException {
        return projectView.getView(branch) != null;
    }

    private void createBranchView(String branch, NestedView projectView) throws IOException {
        ListView branchView = (ListView)HUDSON.getView(branch);
        if (branchView == null) {
            branchView = new ListView(branch, projectView);
            branchView.setIncludeRegex("^.*_" + branch + "$");
            projectView.addView(branchView);
            branchView.save();
        }
    }

    private NestedView getProjectView(String project) throws IOException {
        NestedView projectView = (NestedView)HUDSON.getView(project);
        if (projectView == null) {
            projectView = new NestedView(project);
            HUDSON.addView(projectView);
            projectView.save();
        }
        return projectView;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link HelloWorldBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * <p/>
     * <p/>
     * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         * <p/>
         * <p/>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private boolean useFrench;

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         * <p/>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        public FormValidation doCheckName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Please set a name");
            if (value.length() < 4)
                return FormValidation.warning("Isn't the name too short?");
            return FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Say hello world";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            useFrench = formData.getBoolean("useFrench");
            // ^Can also use req.bindJSON(this, formData);
            //  (easier when there are many fields; need set* methods for this, like setUseFrench)
            save();
            return super.configure(req, formData);
        }

        /**
         * This method returns true if the global configuration says we should speak French.
         * <p/>
         * The method name is bit awkward because global.jelly calls this method to determine
         * the initial state of the checkbox by the naming convention.
         */
        public boolean getUseFrench() {
            return useFrench;
        }
    }
}

