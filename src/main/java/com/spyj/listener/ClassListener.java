package com.spyj.listener;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.ClassEvent;

/**
 * Listens for BlueJ class lifecycle events and records them as
 * {@code CLASS_CREATED}, {@code CLASS_MODIFIED}, and {@code CLASS_DELETED} telemetry events.
 * Implements the BlueJ {@link bluej.extensions2.event.ClassListener} API.
 *
 * <p>Class creation is distinguished from modification by tracking the set of class files
 * already seen. The first time a class file is observed it is recorded as {@code CLASS_CREATED};
 * all subsequent state changes for that file are recorded as {@code CLASS_MODIFIED}.</p>
 *
 * <p>When a project is opened, {@link #addExistingClasses(BPackage)} must be called to
 * pre-populate the internal tracking set so that pre-existing classes are not misreported
 * as newly created.</p>
 *
 * @author Mykel Dedier
 */
public class ClassListener implements bluej.extensions2.event.ClassListener {

    /**
     * The set of Java source files that have already been observed.
     * Used to distinguish class creation from modification.
     */
    private HashSet<File> createdClasses;

    /** The central logger bus used to publish captured events. */
    private SpyJLogger sjLogger;

    /**
     * Creates a ClassListener that records class lifecycle events to the given logger.
     *
     * @param sjLogger the central event logger
     */
    public ClassListener(SpyJLogger sjLogger) {
        this.createdClasses = new HashSet<>();
        this.sjLogger = sjLogger;
    }

    /**
     * Pre-populates the internal tracking set with the classes that already exist in the
     * given package. This prevents pre-existing classes from being reported as
     * {@code CLASS_CREATED} when their state is first observed after a project opens.
     * Called by {@link ProjectListener#packageOpened(PackageEvent)} on project open.
     *
     * @param bPackage the BlueJ package whose existing classes should be registered
     */
    public void addExistingClasses(BPackage bPackage) {
        try {
            for (BClass bClass : bPackage.getClasses()) {
                createdClasses.add(bClass.getJavaFile());
            }
        } catch (Exception exception) {
            System.out.println("Failed To Load Existing Project Classes");
        }
    }

    /**
     * Called by BlueJ when a class's state changes (e.g. it is saved or compiled).
     * Records a {@code CLASS_CREATED} event the first time the class file is seen,
     * or a {@code CLASS_MODIFIED} event for all subsequent state changes.
     *
     * @param e the BlueJ class event
     */
    public void classStateChanged(ClassEvent e) {
        try {
            BClass cBClass = e.getBClass();
            BPackage pBPackage = cBClass.getPackage();
            File classFile = cBClass.getJavaFile();

            HashMap<String, Object> currClassDetails = new HashMap<>();

            currClassDetails.put("Class", cBClass.getName());
            currClassDetails.put("Class Package", pBPackage.getName());
            currClassDetails.put("Class Compiled", cBClass.isCompiled());

            if (!createdClasses.contains(classFile)) {
                createdClasses.add(classFile);
                sjLogger.recordEvent(EventType.CLASS_CREATED, currClassDetails);
            } else {
                sjLogger.recordEvent(EventType.CLASS_MODIFIED, currClassDetails);
            }
        } catch (Exception exception) {
            System.out.println("Failed To Capture Class Creation/Modification Event");
        }
    }

    /**
     * Called by BlueJ when a class is deleted from the project.
     * Records a {@code CLASS_DELETED} event and removes the class file from the tracking set.
     *
     * @param e the BlueJ class event
     */
    public void classRemoved(ClassEvent e) {
        BClass cBClass = e.getBClass();
        try {
            BPackage pBPackage = cBClass.getPackage();
            File classFile = cBClass.getJavaFile();
            createdClasses.remove(classFile);

            HashMap<String, Object> removedClassDetails = new HashMap<>();

            removedClassDetails.put("Class", cBClass.getName());
            removedClassDetails.put("Class Package", pBPackage.getName());

            sjLogger.recordEvent(EventType.CLASS_DELETED, removedClassDetails);
        } catch (Exception exception) {
            System.out.println("Failed To Capture Class Deletion Event");
        }
    }

    /**
     * Called by BlueJ when a class is renamed.
     * Records a {@code CLASS_MODIFIED} event that includes both the new and old class names,
     * and updates the internal tracking set to reflect the new file path.
     *
     * @param e the BlueJ class event containing the old and new class names
     */
    public void classNameChanged(ClassEvent e) {
        try {
            BClass cBClass = e.getBClass();
            BPackage pBPackage = cBClass.getPackage();
            File newClassFile = cBClass.getJavaFile();

            String fileName = newClassFile.getName();
            String extension = fileName.substring(fileName.lastIndexOf("."));
            File oldClassFile = new File(newClassFile.getParentFile(), e.getOldName() + extension);

            createdClasses.remove(oldClassFile);
            createdClasses.add(newClassFile);

            HashMap<String, Object> currClassDetails = new HashMap<>();

            currClassDetails.put("Class", cBClass.getName());
            currClassDetails.put("Class Old Name", e.getOldName());
            currClassDetails.put("Class Package", pBPackage.getName());
            currClassDetails.put("Class Compiled", cBClass.isCompiled());

            sjLogger.recordEvent(EventType.CLASS_MODIFIED, currClassDetails);
        } catch (Exception exception) {
            System.out.println("Failed To Capture Class Name Modification Event");
        }
    }
}
