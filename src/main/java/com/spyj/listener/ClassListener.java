package com.spyj.listener;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;

import com.spyj.logger.EventType;
import com.spyj.logger.SpyJLogger;

import bluej.extensions2.BClass;
import bluej.extensions2.BPackage;
import bluej.extensions2.event.ClassEvent;

public class ClassListener implements bluej.extensions2.event.ClassListener {

    private HashSet<File> createdClasses;
    private SpyJLogger sjLogger;

    public ClassListener (SpyJLogger sjLogger) {
        this.createdClasses = new HashSet<>();
        this.sjLogger = sjLogger;
    }

    public void addExistingClasses (BPackage bPackage) {
        try {
            for (BClass bClass : bPackage.getClasses()) {
                createdClasses.add(bClass.getJavaFile());
            }
        } catch (Exception exception) {
            System.out.println("Failed To Load Existing Project Classes");
        }
    }

    // Detects Modifications & Creations
    public void classStateChanged (ClassEvent e) {
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
            }
            else {
                sjLogger.recordEvent(EventType.CLASS_MODIFIED, currClassDetails);
            }
        } catch (Exception exception) {
            System.out.println("Failed To Capture Class Creation/Modification Event");
        }
    }

    public void classRemoved (ClassEvent e) {
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
    
    public void classNameChanged (ClassEvent e) {
        try {
            BClass cBClass = e.getBClass();
            BPackage pBPackage = cBClass.getPackage();
            File newclassFile = cBClass.getJavaFile();
            
            String fileName = newclassFile.getName();
            String extension = fileName.substring(fileName.lastIndexOf("."));
            File oldClassFile = new File(newclassFile.getParentFile(), e.getOldName() + extension);
            
            createdClasses.remove(oldClassFile);
            createdClasses.add(newclassFile);

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
