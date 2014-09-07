package org.jenkinsci.plugins.trflow.scm;

public interface ScmClient {

    ScmClient createBranch(String name);
    ScmClient createBranch(String name, ScmHook<String> hook);

    ScmClient deleteBranch(String name);
    ScmClient deleteBranch(String name, ScmHook<String> hook);

    ScmClient releaseBranch(String name, String description);
    ScmClient releaseBranch(String name, String description, ScmHook<String> hook);

    ScmClient deleteRequest(String name);
    ScmClient deleteRequest(String name, ScmHook hook);

    String getRemoteUrl();
    String getRepositoryName();
}
