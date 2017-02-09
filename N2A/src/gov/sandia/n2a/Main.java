/*
Copyright 2013,2016 Sandia Corporation.
Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation,
the U.S. Government retains certain rights in this software.
Distributed under the BSD-3 license. See the file LICENSE for details.
*/

package gov.sandia.n2a;

import gov.sandia.n2a.db.AppData;
import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.ui.AboutDialog;
import gov.sandia.n2a.ui.LafManager;
import gov.sandia.n2a.ui.MainFrame;

import java.awt.EventQueue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import replete.gui.windows.Dialogs;
import replete.gui.windows.common.CommonWindowClosingEvent;
import replete.gui.windows.common.CommonWindowClosingListener;
import replete.plugins.PluginManager;


public class Main
{
    public static void main (String[] args)
    {
        setUncaughtExceptionHandler (null);

        // Set global application properties.
        AppData.properties.set ("Neurons to Algorithms", "name");
        AppData.properties.set ("N2A", "abbreviation");
        AppData.properties.set ("0.92", "version");
        AppData.properties.set ("Fred Rothganger (PI), Derek Trumbo, Christy Warrender, Brad Aimone, Corinne Teeter, Brandon Rohrer, Steve Verzi, Ann Speed, Asmeret Bier, Felix Wang", "developers");
        AppData.properties.set ("Copyright &copy; 2013,2016 Sandia Corporation. Under the terms of Contract DE-AC04-94AL85000 with Sandia Corporation, the U.S. Government retains certain rights in this software.", "copyright");
        AppData.properties.set ("This software is released under the BSD license.  Please refer to the license information provided in this distribution for the complete text.", "license");
        AppData.properties.set ("frothga@sandia.gov", "support");
        AppData.checkInitialDB ();

        // Load plugins
        ArrayList<String> pluginClassNames = new ArrayList<String> ();
        pluginClassNames.add ("gov.sandia.n2a.backend.internal.InternalPlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.xyce.XycePlugin");
        pluginClassNames.add ("gov.sandia.n2a.backend.c.PluginC");
        pluginClassNames.add ("gov.sandia.n2a.backend.neuroml.PluginNeuroML");
        for (String arg : args)
        {
            if (arg.startsWith ("plugin=")) pluginClassNames.add (arg.substring (7));
        }

        File[] pluginDirs = new File[1];
        pluginDirs[0] = new File (AppData.properties.get ("resourceDir"), "plugins");

        if (! PluginManager.initialize (new N2APlugin (), pluginClassNames.toArray (new String[0]), pluginDirs))
        {
            System.err.println (PluginManager.getInitializationErrors ());
        }

        // Create the main frame.
        EventQueue.invokeLater (new Runnable ()
        {
            public void run ()
            {
                LafManager.load ();

                final MainFrame mainFrame = MainFrame.getInstance ();
                mainFrame.addAttemptToCloseListener (new CommonWindowClosingListener ()
                {
                    public void stateChanged (CommonWindowClosingEvent e)
                    {
                        LafManager.save ();

                        MNode winProps = AppData.state.childOrCreate ("WinLayout");
                        winProps.clear ();
                        winProps.set (mainFrame.getX             (), "x");
                        winProps.set (mainFrame.getY             (), "y");
                        winProps.set (mainFrame.getWidth         (), "width");
                        winProps.set (mainFrame.getHeight        (), "height");
                        winProps.set (mainFrame.getExtendedState (), "state");

                        AppData.state.save ();
                    }
                });
                mainFrame.addClosingListener (new ChangeListener ()
                {
                    public void stateChanged (ChangeEvent e)
                    {
                        AppData.save ();
                    }
                });

                MNode winProps = AppData.state.childOrCreate ("WinLayout");
                int w = winProps.getOrDefault (-1, "width");
                int h = winProps.getOrDefault (-1, "height");
                int x = winProps.getOrDefault (-1, "x");
                int y = winProps.getOrDefault (-1, "y");
                if (w >= 0  &&  h >= 0) mainFrame.setSize (w, h);
                if (x >= 0  &&  y >= 0) mainFrame.setLocation (x, y);
                else                    mainFrame.setLocationRelativeTo (mainFrame.getParent ());
                mainFrame.ensureOnScreen (true);
                mainFrame.setVisible (true);
                mainFrame.setExtendedState (winProps.getOrDefault (0, "state"));

                setUncaughtExceptionHandler (mainFrame);
                Dialogs.registerApplicationWindow (mainFrame, AppData.properties.get ("name"));
            }
        });

        // Build labels for the About dialog as the last thing this thread does, effectively in the background.
        AboutDialog.initializeLabels ();
    }

    public static void setUncaughtExceptionHandler (final JFrame parent)
    {
        Thread.setDefaultUncaughtExceptionHandler (new UncaughtExceptionHandler ()
        {
            public void uncaughtException (Thread thread, final Throwable throwable)
            {
                File crashdump = new File (AppData.properties.get ("resourceDir"), "crashdump");
                try
                {
                    PrintStream err = new PrintStream (crashdump);
                    throwable.printStackTrace (err);
                    err.close ();
                }
                catch (FileNotFoundException e) {}

                JOptionPane.showMessageDialog
                (
                    parent,
                    "<html><body><p style='width:300px'>Our apologies, but an exception was raised.<br>"
                    + "<br>Details have been saved in " + crashdump.getAbsolutePath () + "<br>"
                    + "<br>Please consider filing a bug report on github, including the crashdump file and a description of what you were doing at the time.</p></body></html>",
                    "Internal Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
