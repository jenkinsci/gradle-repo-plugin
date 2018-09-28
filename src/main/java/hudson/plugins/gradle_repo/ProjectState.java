package hudson.plugins.gradle_repo;

import hudson.scm.SCMRevisionState;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ProjectState records the state of the repository for a particular build.
 * It is used to see what changed from build to build.
 */
@SuppressWarnings("serial")
public class ProjectState extends SCMRevisionState implements Serializable {

    private String branch;

    public ModuleState project;
    public Map<String, ModuleState> modules = new TreeMap<String, ModuleState>();

    private static Logger debug = Logger.getLogger("hudson.plugins.gradle_repo.ProjectState");

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof ProjectState) {
            final ProjectState other = (ProjectState) obj;
            if (branch == null) {
                if (other.branch != null) {
                    return false;
                }
                return modules.equals(other.modules);
            }
            return branch.equals(other.branch)
                    && modules.equals(other.modules);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return (branch != null ? branch.hashCode() : 0)
                ^ modules.hashCode();
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    /**
     * Returns the manifest repository's branch name when this state was
     * created.
     */
    public String getBranch() {
        return branch;
    }

    public void addProject(String path, String origin, String branch, String revision) {
        modules.put(path, ModuleState.constructCachedInstance(path, origin, branch, revision));
    }

    /**
     * Returns the revision for the repository at the specified path.
     *
     * @param path The path to the repository in which we are interested.
     * @return the SHA1 revision of the repository.
     */
    public String getRevision(final String path) {
        ModuleState project = modules.get(path);
        return project == null ? null : project.getRevision();
    }

    /**
     * Calculate what has changed from a specified previous repository state.
     *
     * @param previousState The previous repository state in which we are interested
     * @return A List of ProjectStates from the previous gradle_repo state which have
     * since been updated.
     */
    List<ModuleState> whatChanged(@Nullable final ProjectState previousState) {
        final List<ModuleState> changes = new ArrayList<ModuleState>();
        if (previousState == null) {
            // Everything is new. The change log would include every change,
            // which might be a little unwieldy (and take forever to
            // generate/parse). Instead, we will return null (no changes)
            debug.log(Level.INFO, "Everything is new");
            return null;
        }
        final Set<String> keys = modules.keySet();
        HashMap<String, ModuleState> previousStateCopy = new HashMap<String, ModuleState>(previousState.modules);
        for (final String key : keys) {
            debug.log(Level.INFO, "key: " + key);
            final ModuleState status = previousStateCopy.get(key);
            if (status == null) {
                // This is a new project, just added to the manifest.
                final ModuleState newProject = modules.get(key);
                debug.log(Level.INFO, "New project: " + key);
                changes.add(ModuleState.constructCachedInstance(newProject.getPath(), newProject.getOrigin(), newProject.getBranch(), null));
            } else if (!status.equals(modules.get(key))) {
                changes.add(status);
            }
            previousStateCopy.remove(key);
        }
        return changes;
    }
}
