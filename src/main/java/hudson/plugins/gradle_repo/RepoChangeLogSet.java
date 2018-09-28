package hudson.plugins.gradle_repo;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;

import java.util.Iterator;
import java.util.List;

/**
 * A ChangeLogSet, which is used when generating the list of changes from one
 * build to the next.
 */
public class RepoChangeLogSet extends ChangeLogSet<ChangeLogEntry> {
    private final List<ChangeLogEntry> logs;

    /**
     * Object Constructor. Call the super class, initialize our variable, and
     * set us as the parent for all of our children.
     *
     * @param build
     *            The build which caused this change log.
     * @param browser
     *            Repository browser.
     * @param logs
     *            a list of RepoChangeLogEntry, containing every change (commit)
     *            which has occurred since the last build.
     */
    RepoChangeLogSet(final Run build,
                     final RepositoryBrowser<?> browser, final List<ChangeLogEntry> logs) {
        super(build, browser);
        this.logs = logs;
        for (final ChangeLogEntry log : logs) {
            log.setParent(this);
        }
    }

    /**
     * Returns an iterator for our RepoChangeLogEntry list. This is used when
     * generating the Web UI.
     */
    public Iterator<ChangeLogEntry> iterator() {
        return logs.iterator();
    }

    @Override
    public boolean isEmptySet() {
        return logs.isEmpty();
    }

    @Override
    public String getKind() {
        return "gradle repo";
    }
}
