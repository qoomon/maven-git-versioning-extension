package me.qoomon.maven.extension.gitversioning;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import org.apache.maven.session.scope.internal.SessionScope;

import java.util.Optional;

/**
 * Created by qoomon on 30/11/2016.
 */
public class SessionScopeUtil {

    public static <T> Optional<T> get(SessionScope sessionScope, Class<T> valueType) {
        try {
            T value = sessionScope.scope(Key.get(valueType), null).get();
            return Optional.ofNullable(value);
        } catch (OutOfScopeException ex) {
            return Optional.empty();
        }
    }

}
