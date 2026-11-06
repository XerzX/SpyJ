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

public class ProjectListener implements PackageListener {

    private BlueJ bluej;
    private SpyJLogger sjLogger;
    private ClassListener cListener;
    private HashSet<File> openedProjectsByDir;

    public ProjectListener (BlueJ bluej, SpyJLogger sjLogger, ClassListener cListener) {
        this.bluej = bluej;
        this.sjLogger = sjLogger;
        this.cListener = cListener;
        this.openedProjectsByDir = new HashSet<>();
    }

    public void packageOpened (PackageEvent e) {
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

    public void packageClosing (PackageEvent e) {
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

    private boolean isProjectClosed (File dir) {
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
