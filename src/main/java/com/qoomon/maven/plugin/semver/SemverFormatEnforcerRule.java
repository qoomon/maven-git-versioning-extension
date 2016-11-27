package com.qoomon.maven.plugin.semver;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

/**
 * Created by qoomon on 24/11/2016.
 */
public final class SemverFormatEnforcerRule implements EnforcerRule {


    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            MavenSession session = (MavenSession) helper.evaluate("${session}");
            String version = session.getCurrentProject().getVersion();
            if(!Semver.PATTERN.matcher(version).matches()){
                throw new EnforcerRuleException("Version does not match Semver Format " + Semver.DESCRIPTION + " see http://semver.org");
            }
        } catch (ExpressionEvaluationException ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(EnforcerRule enforcerRule) {
        return false;
    }

    @Override
    public String getCacheId() {
        return "0";
    }
}