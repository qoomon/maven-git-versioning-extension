package com.qoomon.maven.extension.branchversioning;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.session.scope.internal.SessionScope;

/**
 * Created by qoomon on 30/11/2016.
 */
public class SessionScopeUtil {

    public static MavenSession getMavenSession(SessionScope sessionScope) {
        try {
            return sessionScope.scope(Key.get(MavenSession.class), null).get();
        } catch (OutOfScopeException ex) {
            return null;
        }
    }
}
