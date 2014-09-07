package org.jenkinsci.plugins.trflow.scm;

public class ScmBaseHook<T> implements ScmHook<T> {

    protected ScmBaseHook() {
    }

    public void before(ScmClient client, T obj) {
    }

    public void after(ScmClient client, T obj) {
    }


    public static <X> ScmHook<X> NOP() {
        return new ScmBaseHook<>();

    }
}
