package org.jenkinsci.plugins.trflow.scm.git;


import com.google.common.base.Predicate;

import javax.annotation.Nullable;

class IsReleaseBranchPredicate implements Predicate<String> {
    public static final IsReleaseBranchPredicate IS_RELEASE_BRANCH = new IsReleaseBranchPredicate();

    public boolean apply(@Nullable String s) {
        return s != null && s.toLowerCase().startsWith("release");
    }
}
