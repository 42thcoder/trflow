package org.jenkinsci.plugins.trflow.webhook;

import hudson.model.*;

public class JiraEventCause extends Cause {
    private final JiraEvent event;

    public JiraEventCause(JiraEvent event) {
        this.event = event;
    }

    @Override
    public void onAddedTo(AbstractBuild build) {
        //TODO: I don't thinks this would work!
        build.getBuildVariables().put("jira.event.type", event.getType().name());
        build.getBuildVariables().put("jira.issue.key", event.getIssue().getKey());
        build.getBuildVariables().put("jira.issue.type", event.getIssue().getType());
        build.getBuildVariables().put("jira.issue.status", event.getIssue().getStatus());
        build.getBuildVariables().put("jira.issue.projectKey", event.getIssue().getProjectkey());
    }

    public JiraIssue getIssue() {
        return event.getIssue();
    }

    @Override
    public String getShortDescription() {
        return "[" + event.getIssue().getType() + "/" + event.getIssue().getKey() + "]" + event.getType() + " in Jira";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JiraEventCause that = (JiraEventCause) o;

        if (!event.equals(that.event)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return event.hashCode();
    }

    public Action getParameters() {
        return new ParametersAction(
                new StringParameterValue("branch", event.getIssue().getKey()),

                new StringParameterValue("jira.event.type", event.getType().name()),
                new StringParameterValue("jira.issue.key", event.getIssue().getKey()),
                new StringParameterValue("jira.issue.type", event.getIssue().getType()),
                new StringParameterValue("jira.issue.status", event.getIssue().getStatus()),
                new StringParameterValue("jira.project.key", event.getIssue().getProjectkey())
        );
    }
}