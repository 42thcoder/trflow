package org.jenkinsci.plugins.trflow.webhook;

import net.sf.json.JSONObject;

public class JiraIssue {
    private final JSONObject o;

    protected JiraIssue(JSONObject o) {
        this.o = o;
    }

    public String getStatus() {
        return o.getJSONObject("fields").getJSONObject("status").getString("name");
    }

    public String getKey() {
        return o.getString("key");
    }

    public String getType() {
        return o.getJSONObject("fields").getJSONObject("issuetype").getString("name");
    }

    public String getProjectkey() {
        return o.getJSONObject("fields").getJSONObject("project").getString("key");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JiraIssue jiraIssue = (JiraIssue) o;

        if (!getKey().equals(jiraIssue.getKey())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public String toString() {
        return o.toString();
    }
}
