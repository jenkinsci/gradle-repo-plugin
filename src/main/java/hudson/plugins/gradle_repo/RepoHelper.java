package hudson.plugins.gradle_repo;

import hudson.FilePath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class RepoHelper {

    public static ProjectState getProjectState(FilePath workspace, boolean includeRevision, GitHelper gitHelper, PrintStream logger) {
        ProjectState projectState = new ProjectState();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            FilePath repoFile = new FilePath(workspace, "repo.xml");
            Document doc = builder.parse(repoFile.read());
            Element rootElement = doc.getDocumentElement();

            // project
            NodeList projectNodeList = rootElement.getElementsByTagName("project");
            if (projectNodeList.getLength() > 1) {
                throw new IllegalArgumentException("[repo] - there are multiple <project /> element.");
            }
            List<String> includeModuleList = new ArrayList<String>();
            if (projectNodeList.getLength() != 1) {
                throw new RuntimeException("[repo] - without <project /> element.");
            }

            Element projectElement = (Element) projectNodeList.item(0);

            // project origin url
            String origin = projectElement.getAttribute("origin");
            if (origin == null || origin.trim().equals("")) {
                throw new RuntimeException("[repo] - <project /> element [origin] is not set.");
            }
            if (!(origin.startsWith("http") || origin.startsWith("git@")) || !origin.endsWith(".git")) {
                throw new IllegalArgumentException("[repo] - <project /> element [origin] is not valid.");
            }

            // project origin branch
            String branch = projectElement.getAttribute("branch");
            if (branch == null || branch.trim().equals("")) {
                branch = "master";
            }
            projectState.setBranch(branch);

            // project current revision
            String revision = null;
            if (includeRevision && workspace.exists() && gitHelper.isGit(workspace)) {
                revision = gitHelper.getRevision(workspace);
            }
            projectState.project = ModuleState.constructCachedInstance("./", origin, branch, revision);
//            logger.println("project, origin: " + origin + ", branch: " + branch + ", revision: " + revision);

            // project include module
            NodeList includeModuleNodeList = projectElement.getElementsByTagName("include");
            for (int i = 0; i < includeModuleNodeList.getLength(); i++) {
                Element includeModuleElement = (Element) includeModuleNodeList.item(i);
                String moduleName = includeModuleElement.getAttribute("module");
                includeModuleList.add(moduleName.trim());
            }

            NodeList moduleNodeList = rootElement.getElementsByTagName("module");
            for (int i = 0; i < moduleNodeList.getLength(); i++) {
                Element moduleElement = (Element) moduleNodeList.item(i);
                String name = moduleElement.getAttribute("name");
                if (name == null || name.trim().equals("")) {
                    throw new RuntimeException("[repo] - <module /> element [name] is not set.");
                }

                // filter module
                if (includeModuleList.contains(name)) continue;

                // module path
                String path;
                String local = moduleElement.getAttribute("local");
                if (local == null || local.trim().equals("")) {
                    local = "./";
                }
                if (local.endsWith("/")) {
                    path = local + name;
                } else {
                    path = local + "/" + name;
                }

                // module origin url
                String moduleOrigin = moduleElement.getAttribute("origin");
                if ((moduleOrigin.startsWith("http") || moduleOrigin.startsWith("git@")) && moduleOrigin.endsWith(".git")) {

                } else {
                    if (!moduleOrigin.startsWith(".")) {
                        throw new IllegalArgumentException("[repo] - if <module /> element [origin] is relative path, must start with './' or '../'.");
                    }
                    if (moduleOrigin.endsWith(".git")) {
                        moduleOrigin = moduleOrigin.replace(".git", "");
                    }
                    if (projectState.project == null) {
                        throw new IllegalArgumentException("[repo] - if <module /> element [origin] is relative path, must set <project /> element [origin].");
                    }
                    moduleOrigin = filterRelativeOrigin(projectState.project.getOrigin(), moduleOrigin);
                }

                String moduleBranch = moduleElement.getAttribute("branch");
                if (moduleBranch.trim().equals("")) {
                    moduleBranch = projectState.project.getBranch();
                }

                // module origin revision
                String moduleRevision = null;
                if(includeRevision) {
                    FilePath moduleDir = new FilePath(workspace, path);
                    if (moduleDir.exists() && gitHelper.isGit(moduleDir)) {
                        moduleRevision = gitHelper.getRevision(moduleDir);
                    }
                }
                projectState.addProject(path, moduleOrigin, moduleBranch, moduleRevision);
//                logger.println("module: " + name + ", origin: " + moduleOrigin + ", branch: " + moduleBranch + ", path: " + path + ", revision: " + moduleRevision);
            }
        } catch (final Exception e) {
            if (logger != null) {
                logger.println(e);
            }
        }
        return projectState;
    }

    static String filterRelativeOrigin(String projectOrigin, String moduleOrigin) throws URISyntaxException {
        String projectUrl, projectPath;
        boolean isSSH = false;
        if (projectOrigin.startsWith("git@")) {
            isSSH = true;
            String[] temp = projectOrigin.split(":");
            projectUrl = temp[0];
            projectPath = temp[1];
        } else {
            URI uri = new URI(projectOrigin);
            projectPath = uri.getPath();
            projectUrl = projectOrigin.replace(projectPath, "");
        }

        String prefix = "http://a.com";
        URI tempUri = new URI(prefix + projectPath);
        tempUri = tempUri.resolve(moduleOrigin);
        String tempUrl = tempUri.toString();
        tempUrl = tempUrl.replace(prefix, "");

        return projectUrl + (isSSH ? ":" : "") + tempUrl + ".git";
    }
}
