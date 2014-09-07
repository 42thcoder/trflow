package org.jenkinsci.plugins.trflow.webhook;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.RootAction;
import hudson.model.UnprotectedRootAction;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.Scanner;
import java.util.logging.Logger;

import static net.sf.json.JSONObject.fromObject;

/**
 * Receives Jira hook (see <a href="https://developer.atlassian.com/display/JIRADEV/JIRA+Webhooks+Overview">JIRA Webhooks Overview</a>).
 *
 * @author Diego Casas Rodriguez
 */
@Extension
public class JiraWebHook implements UnprotectedRootAction {
    private static final Logger LOGGER = Logger.getLogger(JiraWebHook.class.getName());
    public static final String URLNAME = "jira-webhook";

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return URLNAME;
    }

    /*

{
    "id": 2,
    "timestamp": "2009-09-09T00:08:36.796-0500",
    "issue": {
        "expand":"renderedFields,names,schema,transitions,operations,editmeta,changelog",
        "id":"99291",
        "self":"https://jira.atlassian.com/rest/api/2/issue/99291",
        "key":"JRA-20002",
        "fields":{
            "summary":"I feel the need for speed",
            "created":"2009-12-16T23:46:10.612-0600",
            "description":"Make the issue nav load 10x faster",
            "labels":["UI", "dialogue", "move"],
            "priority": "Minor"
        }
    },
    "user": {
        "self":"https://jira.atlassian.com/rest/api/2/user?username=brollins",
        "name":"brollins",
        "emailAddress":"bryansemail at atlassian dot com",
        "avatarUrls":{
            "16x16":"https://jira.atlassian.com/secure/useravatar?size=small&avatarId=10605",
            "48x48":"https://jira.atlassian.com/secure/useravatar?avatarId=10605"
        },
        "displayName":"Bryan Rollins [Atlassian]",
        "active" : "true"
    },
    "changelog": {
        "items": [
            {
                "toString": "A new summary.",
                "to": null,
                "fromString": "What is going on here?????",
                "from": null,
                "fieldtype": "jira",
                "field": "summary"
            },
            {
                "toString": "New Feature",
                "to": "2",
                "fromString": "Improvement",
                "from": "4",
                "fieldtype": "jira",
                "field": "issuetype"
            }
        ],
        "id": 10124
    },
    "comment" : {
        "self":"https://jira.atlassian.com/rest/api/2/issue/10148/comment/252789",
        "id":"252789",
        "author":{
            "self":"https://jira.atlassian.com/rest/api/2/user?username=brollins",
            "name":"brollins",
            "emailAddress":"bryansemail@atlassian.com",
            "avatarUrls":{
                "16x16":"https://jira.atlassian.com/secure/useravatar?size=small&avatarId=10605",
                "48x48":"https://jira.atlassian.com/secure/useravatar?avatarId=10605"
            },
            "displayName":"Bryan Rollins [Atlassian]",
            "active":true
        },
        "body":"Just in time for AtlasCamp!",
        "updateAuthor":{
            "self":"https://jira.atlassian.com/rest/api/2/user?username=brollins",
            "name":"brollins",
            "emailAddress":"brollins@atlassian.com",
            "avatarUrls":{
                "16x16":"https://jira.atlassian.com/secure/useravatar?size=small&avatarId=10605",
                "48x48":"https://jira.atlassian.com/secure/useravatar?avatarId=10605"
            },
            "displayName":"Bryan Rollins [Atlassian]",
            "active":true
        },
        "created":"2011-06-07T10:31:26.805-0500",
        "updated":"2011-06-07T10:31:26.805-0500"
    },
    "timestamp": "2011-06-07T10:31:26.805-0500",
    "webhookEvent": "jira:issue_updated"
}
     */


    /**
     * Receives the webhook call.
     */
    @RequirePOST
    public void doIndex(StaplerRequest req) {
        try {
            final JSONObject payload = fromObject(new Scanner(req.getInputStream()).useDelimiter("\\A").next());
            LOGGER.warning("Received request: " + payload.toString());
            final JiraEvent event = new JiraEvent(payload);
            if (event.hasStatusChanged()) // we only care when status changed
                processPayload(event);
            else
                LOGGER.warning("ignoring event. Status has not changed.");
        } catch (Exception e) {
            LOGGER.severe(e.getMessage());
            e.printStackTrace();
        }
    }

    public void processPayload(JiraEvent event) {
        LOGGER.info(String.format("Received event '%s' for issue %s ", event, event.getIssue().getKey()));

        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because when we actually schedule a build, it's a build that can
        // happen at some random time anyway.
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {

            for (AbstractProject<?, ?> job : Hudson.getInstance().getAllItems(AbstractProject.class))
                triggerJob(job.getTrigger(JiraTrigger.class), job, event);

        } finally {
            SecurityContextHolder.getContext().setAuthentication(old);
        }
        for (Listener listener : Jenkins.getInstance().getExtensionList(Listener.class)) {
            listener.onEventReceived(event);
        }
    }

    protected void triggerJob(JiraTrigger trigger, AbstractProject<?, ?> job, JiraEvent event) {
        if (trigger != null) {
            LOGGER.fine("poking job " + job.getFullDisplayName());
            trigger.scheduleBuild(new JiraEventCause(event));
        }
    }

    public static JiraWebHook get() {
        return Hudson.getInstance().getExtensionList(RootAction.class).get(JiraWebHook.class);
    }

    /**
     * Other plugins may be interested in listening for these updates.
     */
    public static abstract class Listener implements ExtensionPoint {

        /**
         * Called when an event is received from Jira.
         *
         * @param event the event.
         */
        public abstract void onEventReceived(JiraEvent event);
    }

}