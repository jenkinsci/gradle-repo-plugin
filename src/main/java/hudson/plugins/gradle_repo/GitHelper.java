package hudson.plugins.gradle_repo;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class GitHelper {

    private Launcher launcher;
    private EnvVars env;
    private PrintStream logger;

    public GitHelper(Launcher launcher, EnvVars env, PrintStream logger) {
        this.launcher = launcher;
        this.env = env;
        this.logger = logger;
    }

    public boolean isGit(FilePath moduleDir) {
        try {
            return new FilePath(moduleDir, ".git").exists();
        } catch (Exception e) {
            throw new RuntimeException("[repo] - fail to check file [" + moduleDir.getName() + "] has .git or not.");
        }
    }

    public void clone(FilePath moduleDir, String repositoryUrl, String branch) {
        try {
            if(!moduleDir.exists()) {
                moduleDir.mkdirs();
            }
        } catch (Exception e) {
            throw new RuntimeException("[repo] - fail to mkdirs [\"" + moduleDir.getName() + "\"].");
        }
        List<String> commands = new ArrayList<String>(7);
        commands.add("git");
        commands.add("clone");
        commands.add(env.expand(repositoryUrl));
        if (branch != null) {
            commands.add("-b");
            commands.add(env.expand(branch));
        }
        commands.add("-l");
        commands.add(moduleDir.getName());

        int resultCode;
        try {
            resultCode = launcher.launch().stdout(logger).pwd(moduleDir.getParent()).cmds(commands).envs(env).join();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("[repo] - git fail to execute [" + array2String(commands) + "]");
        }
        if (resultCode != 0) {
            throw new RuntimeException("[repo] - git fail to execute [" + array2String(commands) + "]");
        }
    }

    public void pull(FilePath moduleDir, String branch) {
        List<String> commands = new ArrayList<String>(4);
        commands.add("git");
        commands.add("pull");
        commands.add("origin");
        commands.add(branch);
        execute(moduleDir, commands);
    }

    public void checkoutBranchIfChange(FilePath moduleDir, String branchName) {
        if(!getBranchName(moduleDir).equals(branchName)) {
            if (isLocalBranch(moduleDir, branchName)) {
                checkoutBranch(moduleDir, branchName);
            } else {
                if (isRemoteBranch(moduleDir, branchName)) {
                    checkoutRemoteBranch(moduleDir, branchName);
                } else {
                    checkoutNewBranch(moduleDir, branchName);
                }
            }
        }
    }

    public void checkoutBranch(FilePath moduleDir, String branchName) {
        List<String> commands = new ArrayList<String>(3);
        commands.add("git");
        commands.add("checkout");
        commands.add(branchName);
        execute(moduleDir, commands);
    }

    public void checkoutRemoteBranch(FilePath moduleDir, String branchName) {
        List<String> commands = new ArrayList<String>(5);
        commands.add("git");
        commands.add("checkout");
        commands.add("-b");
        commands.add(branchName);
        commands.add("origin/" + branchName);
        execute(moduleDir, commands);
    }

    public void checkoutNewBranch(FilePath moduleDir, String branchName) {
        List<String> commands = new ArrayList<String>(4);
        commands.add("git");
        commands.add("checkout");
        commands.add("-b");
        commands.add(branchName);
        execute(moduleDir, commands);
    }

    public String getBranchName(FilePath moduleDir) {
        List<String> commands = new ArrayList<String>(5);
        commands.add("git");
        commands.add("symbolic-ref");
        commands.add("--short");
        commands.add("-q");
        commands.add("HEAD");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        execute(moduleDir, commands, output);

        try {
            return output.toString("UTF-8").trim();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isLocalBranch(FilePath moduleDir, String branchName) {
        try {
            return new FilePath(moduleDir, ".git/refs/heads/" + branchName).exists();
        } catch (InterruptedException e) {
            throw new RuntimeException("[repo] - fail to check file [\"" + moduleDir.getName() + "\"] is local branch or not.");
        } catch (IOException e) {
            throw new RuntimeException("[repo] - fail to check file [\"" + moduleDir.getName() + "\"] is local branch or not.");
        }
    }

    public boolean isRemoteBranch(FilePath moduleDir, String branchName) {
        List<String> commands = new ArrayList<String>(5);
        commands.add("git");
        commands.add("fetch");
        execute(moduleDir, commands);

        if(branchName.equals("master")) {
            branchName = "HEAD";
        }
        try {
            return new FilePath(moduleDir, ".git/refs/remotes/origin/" + branchName).exists();
        } catch (InterruptedException e) {
            throw new RuntimeException("[repo] - fail to check file [\"" + moduleDir.getName() + "\"] is remote branch or not.");
        } catch (IOException e) {
            throw new RuntimeException("[repo] - fail to check file [\"" + moduleDir.getName() + "\"] is remote branch or not.");
        }
    }

    public String getRevision(FilePath moduleDir) {
        final List<String> commands = new ArrayList<String>(2);
        commands.add("git");
        commands.add("rev-parse");
        commands.add("HEAD");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        execute(moduleDir, commands, output);

        try {
            return output.toString("UTF-8").trim();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void execute(FilePath moduleDir, List<String> commands) {
        execute(moduleDir, commands, logger);
    }

    private void execute(FilePath moduleDir, List<String> commands, OutputStream out) {
        int resultCode;
        try {
            resultCode = launcher.launch().stdout(out).pwd(moduleDir).cmds(commands).envs(env).join();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("[repo] - git fail to execute [" + array2String(commands) + "]");
        }
        if (resultCode != 0) {
            throw new RuntimeException("[repo] - git fail to execute [" + array2String(commands) + "]");
        }
    }

    private String array2String(List<String> commands) {
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < commands.size(); i++) {
            temp.append(commands.get(i));
            temp.append(" ");
        }
        return temp.toString().trim();
    }
}
