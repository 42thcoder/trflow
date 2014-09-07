package org.jenkinsci.plugins.trflow.scm.git;


import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.IndexEntry;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.trflow.scm.ScmBaseHook;
import org.jenkinsci.plugins.trflow.scm.ScmClient;
import org.jenkinsci.plugins.trflow.scm.ScmHook;
import org.kohsuke.github.*;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.*;

import static com.google.common.collect.Collections2.filter;
import static java.util.Collections.unmodifiableSortedSet;
import static org.jenkinsci.plugins.trflow.scm.git.BranchNameComparator.BRANCH_NAME_COMPARATOR;
import static org.jenkinsci.plugins.trflow.scm.git.IsReleaseBranchPredicate.IS_RELEASE_BRANCH;
import static org.kohsuke.github.GHCompare.Status.identical;
import static org.kohsuke.github.GHIssueState.OPEN;

public class TRGit implements ScmClient {
    private final GitHub hub;
    private final GitClient git;
    private final PrintStream logger;
    private final GHRepository repo;
    private final String repositoryName;

    private static final URIish ORIGIN;

    static {
        try {
            ORIGIN = new URIish("origin");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public TRGit(final AbstractBuild build, final BuildListener listener, String gitHubApiUrl, String gitOrganization, String oauthToken) {
        this(createGitClient(build, listener), createGitHubClient(gitHubApiUrl, gitOrganization, oauthToken), listener.getLogger());
    }

    public TRGit(GitClient git, GitHub hub, PrintStream logger) {
        this(git, hub, getRepository(hub, getRepositoryName(git)), logger);
    }

    public TRGit(GitClient git, GitHub hub, GHRepository repository, PrintStream logger) {
        this.git = git;
        this.hub = hub;
        this.repo = repository;
        this.logger = logger;
        this.repositoryName = getRepositoryName(git);
    }


    public ScmClient createBranch(String name) {
        return createBranch(name, ScmBaseHook.<String>NOP());
    }

    public ScmClient createBranch(String name, ScmHook<String> hook) {
        for (TRGit subGit : getSubmodules())
            subGit.createBranch(name, hook);

        if (!getBranches().contains(name)) {
            hook.before(this, name);
            doBranch(name);
            hook.after(this, name);
        } else
            logger.println("branch '" + name + "' already created on repositoryName '" + repositoryName + "'. No need to create it.");

        return this;
    }

    public ScmClient deleteBranch(String name) {
        return deleteBranch(name, ScmBaseHook.<String>NOP());
    }

    public ScmClient deleteBranch(String name, ScmHook<String> hook) {
        for (TRGit subGit : getSubmodules())
            subGit.deleteBranch(name, hook);

        if (getBranches().contains(name)) {
            hook.before(this, name);
            doDeleteBranch(name);
            hook.after(this, name);
        } else
            logger.println("branch '" + name + "' does not exists on repositoryName '" + repositoryName + "'. No need to delete it.");

        return this;
    }

    public ScmClient releaseBranch(String name, String description) {
        return releaseBranch(name, description, ScmBaseHook.<String>NOP());
    }

    public ScmClient releaseBranch(String name, String description, ScmHook<String> hook) {
        for (TRGit subGit : getSubmodules())
            subGit.releaseBranch(name, description, hook);

        if (!identical.equals(compareBranches(name, getCurrentReleaseBranch()))) {
            hook.before(this, name);
            doCreatePullRequest(name, description);
            hook.after(this, name);
        } else
            logger.println("Branches '" + name + "' and '" + getCurrentReleaseBranch() + "' on repository " + getRepositoryName(git) + " are identical. No need to create a pull request.");

        return this;
    }

    public ScmClient deleteRequest(String name) {
        return this;
    }

    public ScmClient deleteRequest(String name, ScmHook hook) {
        return this;
    }

    public List<TRGit> getSubmodules() {
        List<TRGit> clnts = new ArrayList<>();
        for (IndexEntry m : getGitSubmodules()) {
            final GitClient subgit = git.subGit(m.getFile());
            clnts.add(new TRGit(subgit, hub, getRepository(hub, getRepositoryName(subgit)), logger));
        }
        return clnts;
    }

    public String getCurrentReleaseBranch() {
        return getReleaseBranches().last();
    }

    public SortedSet<String> getReleaseBranches() {
        return getBranches(IS_RELEASE_BRANCH);
    }

    public SortedSet<String> getBranches() {
        return getBranches(Predicates.<String>alwaysTrue());
    }

    public SortedSet<String> getBranches(final Predicate<String> filter) {
        return unmodifiableSortedSet(new TreeSet<String>(BRANCH_NAME_COMPARATOR) {{
            addAll(filter(getGitBranches().keySet(), filter));
        }});
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRemoteUrl() {
        return getRemoteUrl(git);
    }

    protected Map<String, GHBranch> getGitBranches() {
        try {
            return repo.getBranches();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected GHPullRequest doCreatePullRequest(String head, String description) {
        SortedSet<String> releases = getReleaseBranches();
        if (releases.isEmpty())
            throw new ReleaseBranchNotFound();

        String base = releases.last(); //CONVENTION: the most recent release created is the only release in dev phase!.

        for (GHPullRequest pr : repo.listPullRequests(OPEN)) {
            if (head.equals(pr.getHead().getLabel())) {
                logger.println("Pull request already exists. Nothing to do");
                return pr;
            }
        }
        logger.println("Creating a pull request from '" + head + "' to '" + base + "' on repository " + getRepositoryName(git));
        try {
            return repo.createPullRequest("Please merge " + head + " into '" + base + "'", head, base, description);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GHCompare.Status compareBranches(String head, String base) {
        try {
            return repo.getCompare(getGitBranches().get(head), getGitBranches().get(base)).getStatus();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void doBranch(String name) {
        try {
            git.branch(name);
            git.push().ref(name).to(ORIGIN).execute();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.println("branch '" + name + "' created on repositoryName '" + repositoryName + "'.");
    }

    protected void doDeleteBranch(String name) {
        try {
            git.deleteBranch(name);
            git.push().ref(name).to(ORIGIN).execute();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        logger.println("branch '" + name + "' deleted on repositoryName '" + repositoryName + "'.");
    }

    protected List<IndexEntry> getGitSubmodules() {
        try {
            return git.getSubmodules("HEAD");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    protected static GHRepository getRepository(GitHub hub, String project) {
        try {
            return hub.getRepository(getOrganization(hub) + "/" + project);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String getOrganization(GitHub hub) {
        try {
            return hub.getMyself().getLogin();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static String getRepositoryName(GitClient git) {
        return getRemoteUrl(git).replaceFirst("^.*/([^/]+).git$", "$1");
    }

    protected static String getRemoteUrl(GitClient git) {
        try {
            return git.getRemoteUrl("origin");
        } catch (InterruptedException e) {
            throw new GitException(e);
        }
    }

    protected static GitClient createGitClient(final AbstractBuild build, final BuildListener listener) {
        try {
            return Git.with(listener, build.getEnvironment(listener)).in(build.getModuleRoot()).using("git").getClient();
        } catch (Throwable t) {
            throw new UnableToCreateGitClient(t);
        }
    }

    protected static GitHub createGitHubClient(String apiUrl, String organization, String oauthToken) {
        try {
            return GitHub.connectToEnterprise(apiUrl, organization, oauthToken);
        } catch (Throwable t) {
            throw new UnableToCreateGitHubClient(t);
        }
    }

    // Exceptions
    public static final class UnableToCreateGitClient extends RuntimeException {
        public UnableToCreateGitClient(Throwable t) {
            super(t);
        }
    }

    public static final class UnableToCreateGitHubClient extends RuntimeException {
        public UnableToCreateGitHubClient(Throwable t) {
            super(t);
        }
    }

    public static final class ReleaseBranchNotFound extends RuntimeException {
    }

}
