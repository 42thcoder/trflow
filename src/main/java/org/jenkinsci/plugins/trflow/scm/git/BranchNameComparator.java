package org.jenkinsci.plugins.trflow.scm.git;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BranchNameComparator implements Comparator<String> {
    public static final BranchNameComparator BRANCH_NAME_COMPARATOR = new BranchNameComparator();

    private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^(.*)-(\\d+)$"); // <issueType>/<projectKey>-<issueNumber> (e.g. "Story/WCO-234")

    BranchNameComparator() {}

    public int compare(String b1, String b2) {
        final Matcher m1 = BRANCH_NAME_PATTERN.matcher(b1);
        final Matcher m2 = BRANCH_NAME_PATTERN.matcher(b2);

        if (m1.matches() && m2.matches()) {
            int c = m1.group(1).compareTo(m2.group(1));
            return (c == 0) ? m1.group(2).compareTo(m2.group(2)) : c;
        } else
            return b1.compareTo(b2);
    }

}
