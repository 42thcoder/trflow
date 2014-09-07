package org.jenkinsci.plugins.trflow.domain.predicate;

import com.google.common.base.Predicate;
import hudson.plugins.git.Branch;

import javax.annotation.Nullable;

public class ByBranchName implements Predicate<Branch> {
    private final String branch;

    public ByBranchName(String branch) {
        this.branch = branch;
    }

    public boolean apply(@Nullable Branch b) {
        return b.getName().equals(branch);
    }
}
