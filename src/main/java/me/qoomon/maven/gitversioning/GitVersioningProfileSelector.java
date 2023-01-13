package me.qoomon.maven.gitversioning;

import org.apache.maven.model.Profile;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.profile.DefaultProfileSelector;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.ProfileSelector;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.*;
import static org.slf4j.LoggerFactory.getLogger;

@Named
@Singleton
@Component(role = ProfileSelector.class)
public class GitVersioningProfileSelector extends DefaultProfileSelector {

    final private Logger logger = getLogger(GitVersioningProfileSelector.class);

    @Override
    public List<Profile> getActiveProfiles(Collection<Profile> profiles, ProfileActivationContext context, ModelProblemCollector problems) {
        try {
            return getActiveProfiles_(profiles, context, problems);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Profile> getActiveProfiles_(Collection<Profile> profiles, ProfileActivationContext context, ModelProblemCollector problems) throws IOException {
        List<Profile> activeProfiles = super.getActiveProfiles(profiles, context, problems);
        if (context.getProjectDirectory() == null) {
            return activeProfiles;
        }

        Map<String, Boolean> desiredProfileStates = getDesiredProfileStates();
        if(desiredProfileStates.isEmpty()){
            return activeProfiles;
        }

        Set<String> activeProfileIds = activeProfiles.stream().map(Profile::getId).collect(toSet());
        return profiles.stream().filter(profile -> {
            Boolean desiredProfileState = desiredProfileStates.get(profile.getId());
            final boolean originalProfileState = activeProfileIds.contains(profile.getId());

            if (desiredProfileState == null) {
                return originalProfileState;
            }

            if (desiredProfileState && !originalProfileState) {
                logger.info("activate profile: " + profile.getId());
                return true;
            }

            if (!desiredProfileState && originalProfileState) {
                logger.info("deactivate profile: " + profile.getId());
                return false;
            }

            return originalProfileState;
        }).collect(toList());
    }

    private Map<String, Boolean> getDesiredProfileStates() {
        // TODO get desired profile states from RefPatchDescription
        return Map.of("test", true);
    }
}
