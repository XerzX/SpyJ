package com.spyj.listener;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.BlueJ;
import bluej.extensions2.BPackage;
import bluej.extensions2.BProject;
import bluej.extensions2.event.PackageEvent;
import bluej.extensions2.event.PackageListener;
import javafx.application.Platform;

/**
 * Listens for BlueJ package (project) lifecycle events and records them as
 * {@code PROJECT_OPENED} and {@code PROJECT_CLOSED} telemetry events.
 * Implements the BlueJ {@link PackageListener} API.
 *
 * <p>Because a BlueJ project may contain multiple packages, this listener deduplicates
 * open events by project directory so that only the first package-open per project fires
 * a {@code PROJECT_OPENED} event.</p>
 *
 * <p>Close detection is deferred to the JavaFX Application Thread via
 * {@link Platform#runLater(Runnable)} so that {@code bluej.getOpenProjects()} already
 * reflects the closed state before the event is recorded.</p>
 *
 * @author Mykel Dedier
 */
public class ProjectListener implements PackageListener {

    /** The BlueJ API instance used to query currently open projects at close time. */
    private BlueJ bluej;

    /** The central logger bus used to publish captured events. */
    private SpyJLogger sjLogger;

    /** The class listener that must be pre-populated with existing classes when a project opens. */
    private ClassListener cListener;

    /** Set of project directory paths for projects that have already fired a {@code PROJECT_OPENED} event. */
    private HashSet<File> openedProjectsByDir;

    /**
     * Creates a ProjectListener that records project lifecycle events to the given logger.
     *
     * @param bluej     the BlueJ API instance, used to check open projects on close
     * @param sjLogger  the central event logger
     * @param cListener the class listener to pre-populate when a project opens
     */
    public ProjectListener(BlueJ bluej, SpyJLogger sjLogger, ClassListener cListener) {
        this.bluej = bluej;
        this.sjLogger = sjLogger;
        this.cListener = cListener;
        this.openedProjectsByDir = new HashSet<>();
    }

    /**
     * Called by BlueJ when a package within a project is opened.
     * Pre-populates the class listener with pre-existing classes, then records a
     * {@code PROJECT_OPENED} event the first time each project directory is seen.
     * Subsequent package opens within the same project are ignored.
     *
     * @param e the BlueJ package event
     */
    public void packageOpened(PackageEvent e) {
        try {
            BPackage currPackage = e.getPackage();
            BProject currProject = e.getPackage().getProject();
            File projectDir = currProject.getDir();

            cListener.addExistingClasses(currPackage);

            if (!openedProjectsByDir.contains(projectDir)) {
                openedProjectsByDir.add(projectDir);
                HashMap<String, Object> currProjectDetails = new HashMap<>();

                currProjectDetails.put("Project", currProject.getName());
                currProjectDetails.put("Project Directory", currProject.getDir().getAbsolutePath());
                currProjectDetails.put("Project Structure", currProject.toString());
                currProjectDetails.put("Current Package Opened", currPackage.getName());

                sjLogger.recordEvent(EventType.PROJECT_OPENED, currProjectDetails);
            }
        } catch (Exception exception) {
            System.out.println("Failed To Capture Project Open Event");
        }
    }

    /**
     * Called by BlueJ when a package within a project is about to close.
     * Defers the actual check and event recording to the JavaFX Application Thread so that
     * {@code bluej.getOpenProjects()} reflects the updated project list before the event is logged.
     * Records a {@code PROJECT_CLOSED} event only when the entire project has closed
     * (i.e. no remaining packages are open for that project directory).
     *
     * @param e the BlueJ package event
     */
    public void packageClosing(PackageEvent e) {
        try {
            BPackage pBPackage = e.getPackage();
            BProject pBProject = pBPackage.getProject();
            String projectName = pBProject.getName();
            File pDir = pBProject.getDir();

            Platform.runLater(() -> {
                if (isProjectClosed(pDir)) {
                    openedProjectsByDir.remove(pDir);
                    HashMap<String, Object> closedProjectDetails = new HashMap<>();
                    closedProjectDetails.put("Project", projectName);
                    closedProjectDetails.put("Project Directory", pDir.getAbsolutePath());
                    sjLogger.recordEvent(EventType.PROJECT_CLOSED, closedProjectDetails);
                }
            });

        } catch (Exception exception) {
            System.out.println("Failed To Capture Project Close Event");
        }
    }

    /**
     * Checks whether the project associated with the given directory is fully closed
     * by querying the BlueJ API for currently open projects.
     * Returns {@code true} if the project is not found in the open project list,
     * or if the query itself throws an exception.
     *
     * @param dir the root directory of the project to check
     * @return {@code true} if the project appears to be closed; {@code false} otherwise
     */
    private boolean isProjectClosed(File dir) {
        try {
            BProject[] openProjects = bluej.getOpenProjects();
            for (BProject bProject : openProjects) {
                if (bProject.getDir().equals(dir)) {
                    return false;
                }
            }
        } catch (Exception exception) {
            return true;
        }

        return true;
    }
}
