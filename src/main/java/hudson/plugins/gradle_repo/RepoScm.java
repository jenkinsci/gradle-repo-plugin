package hudson.plugins.gradle_repo;

import hudson.*;
import hudson.model.*;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main entrypoint of the plugin. This class contains code to store user
 * configuration and to check out the code using a gradle_repo binary.
 */
@ExportedBean
public class RepoScm extends SCM implements Serializable {

    private static Logger debug = Logger.getLogger("hudson.plugins.gradle_repo.RepoScm");


    private final String repositoryUrl;
    private final String branch;

    private GitHelper gitHelper;

    private ProjectState currentState;

    /**
     * Returns the project repository URL.
     */
    @Exported
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    /**
     * Returns the project branch name. By default to "master".
     */
    @Exported
    public String getBranch() {
        return branch;
    }

    /**
     * Merge the provided environment with the <em>default</em> values of
     * the project parameters. The values from the provided environment
     * take precedence.
     *
     * @param environment an existing environment, which contains already
     *                    properties from the current build
     * @param project     the project that is being built
     */
    private EnvVars getEnvVars(final EnvVars environment, final Job<?, ?> project) {
        // create an empty vars map
        final EnvVars finalEnv = new EnvVars();
        final ParametersDefinitionProperty params = project.getProperty(
                ParametersDefinitionProperty.class);
        if (params != null) {
            for (ParameterDefinition param
                    : params.getParameterDefinitions()) {
                if (param instanceof StringParameterDefinition) {
                    final StringParameterDefinition stpd =
                            (StringParameterDefinition) param;
                    final String dflt = stpd.getDefaultValue();
                    if (dflt != null) {
                        finalEnv.put(param.getName(), dflt);
                    }
                }
            }
        }
        // now merge the settings from the last build environment
        if (environment != null) {
            finalEnv.overrideAll(environment);
        }

        EnvVars.resolve(finalEnv);
        return finalEnv;
    }

    /**
     * The constructor takes in user parameters and sets them. Each job using
     * the RepoSCM will call this constructor.
     *
     * @param repositoryUrl The URL for the project repository.
     * @param branch        The URL for the project branch.
     */
    @DataBoundConstructor
    public RepoScm(final String repositoryUrl, final String branch) {
        this.repositoryUrl = repositoryUrl;
        this.branch = Util.fixEmptyAndTrim(branch);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            @Nonnull final Run<?, ?> build, @Nullable final FilePath workspace,
            @Nullable final Launcher launcher, @Nonnull final TaskListener listener
    ) throws IOException, InterruptedException {
        debug.log(Level.INFO, "calcRevisionsFromBuild");
        // We add our SCMRevisionState from within checkout, so this shouldn't
        // be called often. However it will be called if this is the first
        // build, if a build was aborted before it reported the repository
        // state, etc.
        return SCMRevisionState.NONE;
    }

    @Override
    public void checkout(
            @Nonnull final Run<?, ?> build, @Nonnull final Launcher launcher,
            @Nonnull final FilePath workspace, @Nonnull final TaskListener listener,
            @CheckForNull final File changelogFile, @CheckForNull final SCMRevisionState baseline)
            throws IOException, InterruptedException {

        Job<?, ?> job = build.getParent();
        EnvVars env = build.getEnvironment(listener);
        env = getEnvVars(env, job);

        gitHelper = new GitHelper(launcher, env, listener.getLogger());

        if (!workspace.exists()) {
            workspace.mkdirs();
        }

        if (!checkoutCode(workspace, listener.getLogger())) {
            throw new IOException("Could not checkout");
        }

        currentState.modules.put(currentState.project.getPath(), currentState.project);
        build.addAction(currentState);
        final Run previousBuild = build.getPreviousBuild();
        SCMRevisionState previousState = getLastState(previousBuild, currentState.getBranch());

        if (changelogFile != null) {
            ChangeLog.saveChangeLog(currentState, previousState == SCMRevisionState.NONE ? null : (ProjectState) previousState, changelogFile, launcher, workspace, true);
        }
    }

    private boolean checkoutCode(FilePath workspace, final PrintStream logger) throws IOException, InterruptedException {
        if (workspace.listDirectories().size() == 0) {
            gitHelper.clone(workspace, repositoryUrl, branch);
        } else {
            gitHelper.checkoutBranchIfChange(workspace, branch);
            gitHelper.pull(workspace, branch);
        }
        currentState = RepoHelper.getProjectState(workspace, false, gitHelper, logger);
        Set<String> keys = currentState.modules.keySet();
        for (String key : keys) {
            ModuleState moduleState = currentState.modules.get(key);
            FilePath moduleDir = new FilePath(workspace, moduleState.getPath());
            if (moduleDir.listDirectories().size() == 0) {
                gitHelper.clone(moduleDir, moduleState.getOrigin(), moduleState.getBranch());
            } else {
                gitHelper.checkoutBranchIfChange(moduleDir, moduleState.getBranch());
                gitHelper.pull(moduleDir, moduleState.getBranch());
            }
        }
        currentState = RepoHelper.getProjectState(workspace, true, gitHelper, logger);
        return true;
    }

    @Nonnull
    private SCMRevisionState getLastState(final Run<?, ?> lastBuild, final String expandedBranch) {
        if (lastBuild == null) {
            return ProjectState.NONE;
        }
        final ProjectState lastState = lastBuild.getAction(ProjectState.class);
        if (lastState != null && StringUtils.equals(lastState.getBranch(), expandedBranch)) {
            return lastState;
        }
        return getLastState(lastBuild.getPreviousBuild(), expandedBranch);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new ChangeLog();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Nonnull
    @Override
    public String getKey() {
        return new StringBuilder("gradle repo")
                .append(' ')
                .append(getRepositoryUrl())
                .append(' ')
                .append(getBranch())
                .toString();
    }

    /**
     * A DescriptorImpl contains variables used server-wide. In our263 case, we
     * only store the path to the repo executable, which defaults to just
     * "repo". This class also handles some Jenkins housekeeping.
     */
    @Extension
    public static class DescriptorImpl extends SCMDescriptor<RepoScm> {

        /**
         * Call the superclass constructor and load our configuration from the
         * file system.
         */
        public DescriptorImpl() {
            super(null);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Gradle Repo";
        }

        @Override
        public boolean isApplicable(final Job project) {
            return true;
        }
    }

}
