package org.jenkinsci.plugins.trflow.jenkins;

import hudson.maven.MavenModuleSet;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TopLevelItem;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import org.jenkinsci.plugins.trflow.scm.ScmBaseHook;
import org.jenkinsci.plugins.trflow.scm.ScmClient;

import java.io.IOException;
import java.io.PrintStream;

import static java.util.Arrays.asList;

public class DeleteMavenJenkinsJobScmHook extends ScmBaseHook<String> {
    private static final Hudson HUDSON = Hudson.getInstance();
    private final PrintStream logger;

    public DeleteMavenJenkinsJobScmHook(final BuildListener listener) {
        this.logger = listener.getLogger();
    }

    @Override
    public void after(ScmClient scm, String branch) {
        try {
            deleteMavenJob(scm.getRepositoryName() + "_" + branch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void deleteMavenJob(String jobName) throws IOException, InterruptedException {
        MavenModuleSet job = (MavenModuleSet) HUDSON.getItem(jobName);
        if (job != null) {
            logger.println("job '" + jobName + "' already created. Recreating it ...");
            job.delete();
        }
    }
}
