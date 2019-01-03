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

            RepositoryInfo defaultInfo = null;
            NodeList defaultNodeList = rootElement.getElementsByTagName("default");
            if (defaultNodeList.getLength() > 1) {
                throw new RuntimeException("[repo] - Make sure there is only one '<default />' element in repo.xml");
            } else if (defaultNodeList.getLength() == 1) {
                defaultInfo = new RepositoryInfo();
                Element defaultElement = (Element) defaultNodeList.item(0);
                defaultInfo.branch = defaultElement.getAttribute("branch");
                defaultInfo.fetchUrl = defaultElement.getAttribute("fetch");
            }

            // project
            NodeList projectNodeList = rootElement.getElementsByTagName("project");
            if (projectNodeList.getLength() > 1) {
                throw new RuntimeException("[repo] - Make sure there is only one '<project />' element in repo.xml");
            } else if (projectNodeList.getLength() != 1) {
                throw new RuntimeException("[repo] - Not found '<project />' element in repo.xml.");
            }

            Element projectElement = (Element) projectNodeList.item(0);

            String projectOrigin = projectElement.getAttribute("origin");
            if (projectOrigin.trim().isEmpty()) {
                throw new RuntimeException("[repo] - The 'origin' attribute value of the '<project />' element is invalid.");
            }

            if (projectOrigin.startsWith("http") || projectOrigin.startsWith("git@")) {
                projectOrigin = filterOrigin(projectOrigin);
            } else {
                if (defaultInfo != null && defaultInfo.fetchUrl != null) {
                    projectOrigin = filterOrigin(defaultInfo, projectOrigin);
                } else {
                    throw new RuntimeException("[repo] - The 'origin' attribute value of the '<project />' element is invalid.");
                }
            }

            String projectBranch = projectElement.getAttribute("branch");
            if (projectBranch.trim().isEmpty()) {
                if (defaultInfo != null && defaultInfo.branch != null) {
                    projectBranch = defaultInfo.branch;
                } else {
                    projectBranch = "master";
                }
            }
            projectState.setBranch(projectBranch);

            // project current revision
            String projectRevision = null;
            if (includeRevision && workspace.exists() && gitHelper.isGit(workspace)) {
                projectRevision = gitHelper.getRevision(workspace);
            }
            projectState.project = ModuleState.constructCachedInstance("./", projectOrigin, projectBranch, projectRevision);


            if (defaultInfo == null) {
                defaultInfo = new RepositoryInfo();
                defaultInfo.branch = projectBranch;
                String fetchUrl = projectOrigin;
                if (fetchUrl.startsWith("git@")) {
                    String[] temp = fetchUrl.split(":");
                    defaultInfo.fetchUrl = temp[0] + ":" + temp[1].substring(0, temp[1].lastIndexOf("/"));
                } else {
                    URI uri = new URI(fetchUrl);
                    String path = uri.getPath();
                    String parent = path.substring(0, path.lastIndexOf("/"));
                    defaultInfo.fetchUrl = fetchUrl.replace(uri.getPath(), "") + parent;
                }
            }

            List<String> includeModuleList = new ArrayList<String>();
            NodeList includeModuleNodeList = projectElement.getElementsByTagName("include");
            for (int i = 0; i < includeModuleNodeList.getLength(); i++) {
                Element includeModuleElement = (Element) includeModuleNodeList.item(i);
                String moduleName = includeModuleElement.getAttribute("name");
                includeModuleList.add(moduleName.trim());
            }

            NodeList moduleNodeList = rootElement.getElementsByTagName("module");
            for (int i = 0; i < moduleNodeList.getLength(); i++) {
                Element moduleElement = (Element) moduleNodeList.item(i);
                String name = moduleElement.getAttribute("name");
                if (name.trim().isEmpty()) {
                    throw new RuntimeException("[repo] - The 'name' attribute value of the '<module />' element is not configured.");
                }

                // filter module
                if (includeModuleList.contains(name)) continue;

                // module path
                String path;
                String local = moduleElement.getAttribute("local");
                if (local.trim().isEmpty()) {
                    local = "./";
                }
                if (local.endsWith("/")) {
                    path = local + name;
                } else {
                    path = local + "/" + name;
                }

                String moduleOrigin = moduleElement.getAttribute("origin");
                if (moduleOrigin.startsWith("http") || moduleOrigin.startsWith("git@")) {
                    moduleOrigin = filterOrigin(moduleOrigin);
                } else {
                    if (defaultInfo != null && defaultInfo.fetchUrl != null) {
                        moduleOrigin = filterOrigin(defaultInfo, moduleOrigin);
                    } else {
                        throw new RuntimeException("[repo] - The 'origin' attribute value of the '<module />' element is invalid.");
                    }
                }

                String moduleBranch = moduleElement.getAttribute("branch");
                if (moduleBranch.trim().isEmpty()) {
                    if (defaultInfo != null && defaultInfo.branch != null) {
                        moduleBranch = defaultInfo.branch;
                    } else {
                        moduleBranch = "master";
                    }
                }

                // module origin revision
                String moduleRevision = null;
                if (includeRevision) {
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

    private static String filterOrigin(RepositoryInfo defaultInfo, String origin) throws URISyntaxException {
        String url;
        String fetchUrl = defaultInfo.fetchUrl + "/./" + origin;
        if (fetchUrl.startsWith("git@")) {
            String[] temp = fetchUrl.split(":");
            url = temp[0] + ":" + PathUtils.normalize(temp[1], true);
        } else {
            URI uri = new URI(fetchUrl);
            url = fetchUrl.replace(uri.getPath(), "") + PathUtils.normalize(uri.getPath(), true);
        }

        if (!url.endsWith(".git")) {
            url += ".git";
        }
        return url;
    }

    private static String filterOrigin(String origin) throws URISyntaxException {
        String url;
        if (origin.startsWith("git@")) {
            String[] temp = origin.split(":");
            url = temp[0] + ":" + PathUtils.normalize(temp[1], true);
        } else {
            URI uri = new URI(origin);
            url = origin.replace(uri.getPath(), "") + PathUtils.normalize(uri.getPath(), true);
        }

        if (!url.endsWith(".git")) {
            url += ".git";
        }
        return url;
    }

}
