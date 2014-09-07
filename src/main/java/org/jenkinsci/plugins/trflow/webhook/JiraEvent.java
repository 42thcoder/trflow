package org.jenkinsci.plugins.trflow.webhook;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

public class JiraEvent {

    private final JiraEventType type;
    private final JiraIssue issue;
    private boolean changed;

    protected JiraEvent(JSONObject o) {
        type = JiraEventType.valueOfFromId(o.getString("webhookEvent"));
        issue = new JiraIssue(o.getJSONObject("issue"));

        final JSONArray items = o.getJSONObject("changelog").getJSONArray("items");
        for (int i = 0; i < items.size(); i++) {
            final JSONObject item = (JSONObject) items.get(i);
            changed = changed || ("status".equals(item.getString("field")) && !item.getString("from").equals(item.getString("to")));
        }
    }

    //TODO: implement proper changelog domain
    public boolean hasStatusChanged() {
        return changed;
    }

    public JiraEventType getType() {
        return type;
    }

    public JiraIssue getIssue() {
        return issue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JiraEvent jiraEvent = (JiraEvent) o;

        if (!issue.equals(jiraEvent.issue)) return false;
        if (type != jiraEvent.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + issue.hashCode();
        return result;
    }

}
