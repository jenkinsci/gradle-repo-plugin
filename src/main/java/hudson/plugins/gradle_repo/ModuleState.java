package hudson.plugins.gradle_repo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A ModuleState object represents the state of a project. This is used to see
 * when modules have changed. A gradle_repo manifest contains a list of modules, and
 * a build in Hudson has a list of ProjectStates.
 */
public final class ModuleState implements Serializable {

    private final String path;
    private final String origin;
    private final String branch;
    private final String revision;

    private static Logger debug = Logger.getLogger("hudson.plugins.gradle_repo.ModuleState");

    private static Map<Integer, ModuleState> projectStateCache
            = new HashMap<Integer, ModuleState>();

    /**
     * Create an object representing the state of a project.
     *
     * Project state is immutable and cached.
     *
     * @param path
     *            The client-side path of the project
     * @param revision
     *            The SHA-1 revision of the project
     */
    static synchronized ModuleState constructCachedInstance(final String path, final String origin, final String branch, final String revision) {
        ModuleState moduleState = projectStateCache.get(calculateHashCode(path, origin, branch, revision));

        if (moduleState == null) {
            moduleState = new ModuleState(path, origin, branch, revision);
            projectStateCache.put(moduleState.hashCode(), moduleState);
        }

        return moduleState;
    }

    /**
     * Private constructor called by named constructor
     * constructCachedInstance().
     */
    private ModuleState(final String path, String origin, String branch, final String revision) {
        this.path = path;
        this.origin = origin;
        this.branch = branch;
        this.revision = revision;

        debug.log(Level.FINE, "path: " + path + " revision: " + revision);
    }

    /**
     * Enforce usage of the cache when xstream deserializes the
     * ModuleState objects.
     */
    private synchronized Object readResolve() {
        ModuleState moduleState
                = projectStateCache.get(
                calculateHashCode(path, origin, branch, revision));

        if (moduleState == null) {
            projectStateCache.put(this.hashCode(), this);
            moduleState = this;
        }

        return moduleState;
    }


    /**
     * Gets the client-side path of the project.
     */
    public String getPath() {
        return path;
    }

    public String getOrigin() {
        return origin;
    }

    public String getBranch() {
        return branch;
    }

    /**
     * Gets the revision (SHA-1) of the project.
     */
    public String getRevision() {
        return revision;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ModuleState)) {
            return false;
        }
        final ModuleState other = (ModuleState) obj;
        return (path == null ? other.path == null : path.equals(other.path))
                && (origin == null ? other.origin == null : origin
                .equals(other.origin))
                && (branch == null ? other.branch == null : branch
                .equals(other.branch))
                && (revision == null ? other.revision == null : revision
                .equals(other.revision));
    }

    @Override
    public int hashCode() {
        return calculateHashCode(path, origin, branch, revision);
    }

    /**
     * Calculates the hash code of a would-be ModuleState object with
     * the provided parameters.
     *
     * @param path
     *            The client-side path of the project
     * @param revision
     *            The SHA-1 revision of the project
     */
    private static int calculateHashCode(final String path, final String origin, final String branch, final String revision) {
        return 23 + (path == null ? 37 : path.hashCode())
                + (origin == null ? 89 : origin.hashCode())
                + (branch == null ? 169 : branch.hashCode())
                + (revision == null ? 389 : revision.hashCode());
    }
}
