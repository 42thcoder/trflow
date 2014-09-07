trflow
======

add jira webhook

Jenkins WebHook
URLhttp://localhost:8090/jenkins/jira-webhook/
JQLproject = "World Check One" and (type = Story or type = Bug or type = Release) and Sprint in openSprints()
Events
Issue Created
Issue Deleted
Issue Updated
