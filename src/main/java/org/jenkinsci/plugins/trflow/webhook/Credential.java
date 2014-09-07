package org.jenkinsci.plugins.trflow.webhook;


import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;

/**
 * Credential to access Jira.
 *
 * @author Diego Casas Rodriguez
 */
public class Credential extends AbstractDescribableImpl<Credential> {
    public final String username;
    public final String apiUrl;
    public final String oauthAccessToken;

    @DataBoundConstructor
    public Credential(String username, String apiUrl, String oauthAccessToken) {
        this.username = username;
        this.apiUrl = apiUrl;
        this.oauthAccessToken = oauthAccessToken;
    }

    public void login() throws IOException {
        //return Jira.connect(apiUrl, oauthAccessToken);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Credential> {
        @Override
        public String getDisplayName() {
            return ""; // unused
        }

        public FormValidation doValidate(@QueryParameter String apiUrl, @QueryParameter String username, @QueryParameter String oauthAccessToken) throws IOException {
            return FormValidation.ok("Verified");
            //return FormValidation.error("Failed to validate the account");
        }
    }
}