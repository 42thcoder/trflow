package org.jenkinsci.plugins.trflow.scm;

public interface ScmHook<T> {

    void before(ScmClient client, T obj);
    void after(ScmClient client, T obj);

}
