/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
/**
 * Portions Copyright 2011 ForgeRock AS
 */
package org.forgerock.maven.plugins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * This class provides an implementation of an Ant task that may be used to
 * perform various checks to deteermine whether a file is suitable to be
 * committed.  This includes:
 * <ul>
 *   <li>Make sure that the file has the correct "svn:eol-style" property
 *       value.</li>
 *   <li>If a file contains a line that appears to be a comment and includes the
 *       word "copyright", then it should contain the current year.</li>
 * </ul>
 * 
 * @author Peter Major
 * @goal precommit
 */
public class Precommit extends AbstractMojo implements ISVNStatusHandler {

    /**
     * The name of the system property that may be used to prevent copyright date
     * problems from failing the build.
     */
    private static final String IGNORE_COPYRIGHT_ERRORS_PROPERTY =
            "org.opends.server.IgnoreCopyrightDateErrors";
    /**
     * The name of the system property that may be used to prevent svn eol-style
     * problems from failing the build.
     */
    private static final String IGNORE_EOLSTYLE_ERRORS_PROPERTY =
            "org.opends.server.IgnoreEOLStyleErrors";
    /**
     * The list of the extensions that needs to be checked.
     * @parameter
     */
    private String[] extensions = new String[]{
        "bat",
        "c",
        "h",
        "html",
        "java",
        "ldif",
        "Makefile",
        "mc",
        "sh",
        "txt",
        "xml",
        "xsd",
        "xsl"};
    /**
     * List representation of the configured extensions.
     */
    private List<String> extensionList;
    /**
     * The base directory
     *
     * @parameter expression="${basedir}"
     * @required
     * @readonly
     */
    private File basedir;
    // The set of files that appear to have problems with the EOL style.
    private ArrayList<String> eolStyleProblemFiles = new ArrayList<String>();
    // The set of files that appear to have problems with the copyright date.
    private ArrayList<String> copyrightProblemFiles = new ArrayList<String>();
    // The string representation of the current year.
    private String yearString;
    // The overall SVN Client Manager. required with svnkit 1.2.x
    private SVNClientManager ourClientManager = SVNClientManager.newInstance();
    // The property client used to look at file properties.
    private SVNWCClient propertyClient;
    /**
     * Set this to <code>true</code> to ignore missing copyright statements.
     * 
     * @parameter default-value="false"
     */
    private boolean ignoreCopyright = false;
    /**
     * Set this to <code>true</code> to ignore invalid end of line characters.
     * 
     * @parameter default-value="false"
     */
    private boolean ignoreEol = false;
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project = new MavenProject();

    /**
     * Performs the appropriate processing needed for this task.  In this case,
     * it uses SVNKit to identify all modified files in the current workspace.
     * For all source files, look for comment lines containing the word
     * "copyright" and make sure at least one of them contains the current year.
     */
    @Override()
    public void execute() throws MojoExecutionException, MojoFailureException {
        extensionList = Arrays.asList(extensions);

        // Get the year to use in the determination.
        GregorianCalendar calendar = new GregorianCalendar();
        int year = calendar.get(GregorianCalendar.YEAR);
        yearString = String.valueOf(year);

        // Process the base directory and all of its subdirectories.
        propertyClient = ourClientManager.getWCClient();

        try {
            long status = ourClientManager.getStatusClient().doStatus(basedir, SVNRevision.WORKING,
                    SVNDepth.INFINITY, false, false, false, false, this, null);
        } catch (Exception e) {
            getLog().error("Encountered an error while examining "
                    + "Subversion status. No further checks will be performed.", e);
            return;
        }

        boolean fail = false;

        if (!eolStyleProblemFiles.isEmpty()) {
            getLog().warn("Potential svn:eol-style updates needed "
                    + "for the following files:");
            for (String filename : eolStyleProblemFiles) {
                getLog().warn("     " + filename);
            }

            if (!getProperty(IGNORE_EOLSTYLE_ERRORS_PROPERTY, ignoreEol)) {
                fail = true;
                getLog().error("Fix svn:eol-style problems before proceeding, or "
                        + "use '-D" + IGNORE_EOLSTYLE_ERRORS_PROPERTY
                        + "=true' to ignore svn eol-style warnings.");
            }
        }

        if (!copyrightProblemFiles.isEmpty()) {
            getLog().warn("Potential copyright year updates needed "
                    + "for the following files:");
            for (String filename : copyrightProblemFiles) {
                getLog().warn("     " + filename);
            }

            if (!getProperty(IGNORE_COPYRIGHT_ERRORS_PROPERTY, ignoreCopyright)) {
                fail = true;
                getLog().error("Fix copyright date problems before proceeding, "
                        + "or use '-D" + IGNORE_COPYRIGHT_ERRORS_PROPERTY
                        + "=true' to ignore copyright warnings.");
            }
        }

        if (fail) {
            throw new MojoFailureException("Precommit check failed");
        }
    }

    /**
     * Examines the provided status item to determine whether the associated file
     * is acceptable.
     *
     * @param  status  The SVN status information for the file of interest.
     */
    public void handleStatus(SVNStatus status) {
        File file = status.getFile();
        if ((!file.exists()) || (!file.isFile())) {
            // The file doesn't exist (which probably means it's been deleted) or
            // isn't a regular file, so we'll ignore it.
            return;
        }

        String fileName = file.getName();
        int lastPeriodPos = fileName.lastIndexOf('.');
        if (lastPeriodPos > 0) {
            String extension = fileName.substring(lastPeriodPos + 1);
            if (!extensionList.contains(extension.toLowerCase())) {
                // The file doesn't have an extension that we care about, so skip it.
                return;
            }
        } else {
            // The file doesn't have an extension.  We'll still want to check it if
            // it's in a resource/bin directory.
            File parentDirectory = file.getParentFile();
            if ((parentDirectory == null)
                    || (!parentDirectory.getName().equals("bin"))) {
                return;
            }

            parentDirectory = parentDirectory.getParentFile();
            if ((parentDirectory == null)
                    || (!parentDirectory.getName().equals("resource"))) {
                return;
            }
        }

        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(basedir.getPath() + "/")) {
            filePath = filePath.substring(basedir.getPath().length() + 1);
        }

        // Check to make sure that the file has the correct EOL style.
        try {
            SVNPropertyData propertyData =
                    propertyClient.doGetProperty(file, "svn:eol-style",
                    SVNRevision.BASE,
                    SVNRevision.WORKING);
            if ((propertyData == null)
                    || (!propertyData.getValue().getString().equals("native"))) {
                eolStyleProblemFiles.add(filePath);
            }
        } catch (SVNException se) {
            // This could happen if the file isn't under version control.  If so, then
            // we can't check the eol-style but we should at least be able to check
            // the copyright dates, so keep going.
        }

        // Check to see whether the file has a comment line containing a copyright
        // without the current year.
        BufferedReader reader = null;
        try {
            boolean copyrightFound = false;
            boolean correctYearFound = false;
            reader = new BufferedReader(new FileReader(file));
            String line = reader.readLine();
            while (line != null) {
                String lowerLine = line.toLowerCase().trim();
                if (isCommentLine(lowerLine)) {
                    int copyrightPos = lowerLine.indexOf("copyright");
                    if (copyrightPos > 0) {
                        copyrightFound = true;
                        if (lowerLine.indexOf(yearString) > 0) {
                            correctYearFound = true;
                            break;
                        }
                    }
                }

                line = reader.readLine();
            }

            if (copyrightFound && (!correctYearFound)) {
                copyrightProblemFiles.add(filePath);
            }
        } catch (IOException ioe) {
            getLog().error("Could not read file " + filePath
                    + " to check copyright date.");
            getLog().error("No further copyright date checking will be "
                    + "performed.");
            throw new RuntimeException();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e) {
            }
        }
    }

    /**
     * Checks for system properties, if they are supplied, they will override
     * the configuration made in POM.
     * 
     * @param name the name of the JVM property
     * @param def the default value coming from the POM
     * @return the JVM property if setted, default value otherwise
     */
    private boolean getProperty(String name, boolean def) {
        String prop = System.getProperty(name);
        if (prop != null) {
            return Boolean.valueOf(prop);
        }
        return def;
    }

    /**
     * Indicates whether the provided line appears to be a comment line.  It will
     * check for a number of common comment indicators in Java source files,
     * shell scripts, XML files, and LDIF files.
     *
     * @param  lowerLine  The line to be checked.  It should have been coverted to
     *                    all lowercase characters and any leading spaces
     *                    removed.
     *
     * @return  {@code true} if it appears that the line is a comment line, or
     *          {@code false} if not.
     */
    private static boolean isCommentLine(String lowerLine) {
        if (lowerLine.startsWith("/*")
                || lowerLine.startsWith("*")
                || lowerLine.startsWith("//")
                || lowerLine.startsWith("#")
                || lowerLine.startsWith("rem")
                || lowerLine.startsWith("<!--")
                || lowerLine.startsWith("!")) {
            return true;
        } else {
            return false;
        }
    }
}
