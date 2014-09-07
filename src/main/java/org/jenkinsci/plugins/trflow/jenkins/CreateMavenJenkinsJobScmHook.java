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

public class CreateMavenJenkinsJobScmHook extends ScmBaseHook<String> {
    private static final Hudson HUDSON = Hudson.getInstance();
    private final PrintStream logger;
    private final String templateJobName;

    public CreateMavenJenkinsJobScmHook(final BuildListener listener, String templateJobName) {
        this.templateJobName = templateJobName;
        this.logger = listener.getLogger();
    }

    @Override
    public void after(ScmClient scm, String branch) {
        try {
            createMavenJob(branch, scm.getRemoteUrl(), scm.getRepositoryName() + "_" + branch);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void createMavenJob(String gitBranch, String gitRemote, String jobName) throws IOException, InterruptedException {
        MavenModuleSet job = (MavenModuleSet) HUDSON.getItem(jobName);
        if (job != null) {
            logger.println("job '" + jobName + "' already created. Recreating it ...");
            job.delete();
        }

        MavenModuleSet template = (MavenModuleSet) HUDSON.getItem(templateJobName);
        job = (MavenModuleSet) HUDSON.<TopLevelItem>copy(template, jobName);
        final GitSCM oldScm = (GitSCM) job.getScm();
        job.setScm(new GitSCM(
                asList(new UserRemoteConfig(gitRemote, null, null, null)),
                asList(new BranchSpec(gitBranch)),
                oldScm.isDoGenerateSubmoduleConfigurations(),
                oldScm.getSubmoduleCfg(),
                oldScm.getBrowser(),
                oldScm.getGitTool(),
                oldScm.getExtensions()
        ));
        job.save();
        logger.println("Maven build job '" + jobName + "' created tracking repository '" + gitRemote + "' for branch '" + gitBranch + "'.");
    }
}
