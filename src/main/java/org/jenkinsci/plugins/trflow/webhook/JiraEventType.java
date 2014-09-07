package org.jenkinsci.plugins.trflow.webhook;

public enum JiraEventType {
    IssueCreated("jira:issue_created"),
    IssueDeleted("jira:issue_deleted"),
    IssueUpdated("jira:issue_updated"),
    WorklogUpdated("jira:worklog_updated");

    final String id;

    JiraEventType(String id) {
        this.id = id;
    }

    public static JiraEventType valueOfFromId(String id) {
        for (JiraEventType e : values())
            if (e.id.equals(id))
                return e;

        return null;
    }

    @Override
    public String toString() {
        return id.substring("jira:".length()).replace('_', ' ');
    }
}
