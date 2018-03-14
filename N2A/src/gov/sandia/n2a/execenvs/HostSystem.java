/*
Copyright 2013-2018 National Technology & Engineering Solutions of Sandia, LLC (NTESS).
Under the terms of Contract DE-NA0003525 with NTESS,
the U.S. Government retains certain rights in this software.
*/

package gov.sandia.n2a.execenvs;

import gov.sandia.n2a.db.MNode;
import gov.sandia.n2a.execenvs.beans.AllJobInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class HostSystem
{
    public static List<HostSystem> envs = new ArrayList<HostSystem>();

    public static HostSystem windows = new Windows ();
    public static HostSystem linux   = new Linux ();
    public static HostSystem redsky  = new RemoteParallel ();  // TODO: Generalize redsky to arbitrary remote computing platforms
    
    static
    {
        envs.add (linux);
        envs.add (windows);
        envs.add (redsky);
        envs.add (new RemoteGateway ());
    }

    static int jobCount = 0;

    /**
        Determine our local environment and return the appropriate class.
    **/
    public static HostSystem factory (String host)
    {
        if (host.equals ("redsky")) return redsky;  // TODO: generalize this to a collection of configured remote hosts

        // The default is localhost, and in particular linux
        if (System.getProperty ("os.name").startsWith ("Windows")) return windows;
        return linux;
    }

    /*
        TODO: This interface should use NIO as much as possible.
        In particular, remote file access should be encapsulated in a FileSystemProvider.

        TODO: This class should be renamed to "Host", and details about Host X Backend needs another class.
        For example, details about compiling with GCC belong elsewhere.
        OTOH, resource management and process monitoring do belong here.
    */

    public abstract Set<Long>    getActiveProcs    ()                            throws Exception;
    public abstract long         getProcMem        (long pid)                    throws Exception;
    public abstract AllJobInfo   getJobs           ()                            throws Exception;
    public abstract String       createJobDir      ()                            throws Exception;
    public abstract Path         build             (Path source, Path runtime)   throws Exception;
    /**
     * Ensures that runtime object file is newer than sourceFile.  If not, then attempts to build
     * from sourceFile.  If sourceFile does not exist, then throws exception.
     * @return Path to linkable object file.
     */
    public abstract Path         buildRuntime      (Path source)                 throws Exception;
    /**
     * Starts the simulation on the host system.
     * Sets up piping of the program's stdout and stderr to files "out" and "err" respectively.
     * If a file called "in" exists in the jobDir, then pipes it into the program.
     */
    public abstract long         submitJob         (MNode job, String command)   throws Exception;
    public abstract void         killJob           (long pid)                    throws Exception;
    public abstract void         setFileContents   (String path, String content) throws Exception;
    public abstract String       getFileContents   (String path)                 throws Exception;
    public abstract void         deleteJob         (String jobName)              throws Exception;
    public abstract void         downloadFile      (String path, File destPath)  throws Exception;

    public long lastModified (Path path)
    {
        try
        {
            return Files.readAttributes (path, BasicFileAttributes.class).lastModifiedTime ().toMillis ();
        }
        catch (IOException e1)
        {
            return 0;
        }
    }

    public String file (String dirName, String fileName) throws Exception
    {
        return new File (dirName, fileName).getAbsolutePath ();
    }

    public String quotePath (Path path)
    {
        return "'" + path + "'";
    }

    public String getNamedValue (String name)
    {
        return getNamedValue (name, "");
    }

    public String getNamedValue (String name, String defaultValue)
    {
        if (name.equalsIgnoreCase ("name")) {
            return "Generic";
        }
        return defaultValue;
    }

    @Override
    public String toString()
    {
        return getNamedValue ("name");
    }

    public long getMemoryPhysicalTotal ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getTotalPhysicalMemorySize");
        }
        catch (Exception e)
        {
            System.out.println ("Lack getTotalPhysicalMemorySize call");
            return 0;
        }
    }

    public long getMemoryPhysicalFree ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getFreePhysicalMemorySize");
        }
        catch (Exception e)
        {
            System.out.println ("Lack getFreePhysicalMemorySize call");
            return 0;
        }
    }

    public int getProcessorTotal ()
    {
        // We will assume that processors available to JVM are exactly the same as total system processors.
        return Runtime.getRuntime ().availableProcessors ();
    }

    public double getProcessorLoad ()
    {
        OperatingSystemMXBean OS = ManagementFactory.getOperatingSystemMXBean ();
        try
        {
            return (Long) invoke (OS, "getSystemCpuLoad");
        }
        catch (Exception e)
        {
            return OS.getSystemLoadAverage ();  // TODO: known to fail on Windows
        }
    }

    // Utility functions -----------------------------------------------------

    public static Object invoke (Object target, String methodName, Object... args)
    {
        Class<?> clazz = target.getClass ();
        try
        {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for (int a = 0; a < args.length; a++) parameterTypes[a] = args[a].getClass ();
            Method m = clazz.getMethod (methodName, parameterTypes);
            m.setAccessible (true);
            return m.invoke (target, args);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static void stringToFile (File target, String value) throws IOException
    {
        try (FileOutputStream fos = new FileOutputStream (target))
        {
            fos.write (value.getBytes ("UTF-8"));
        }
    }

    public static String streamToString (InputStream input)
    {
        try (BufferedReader reader = new BufferedReader (new InputStreamReader (input)))
        {
            return reader.lines ().collect (Collectors.joining ("\n"));
        }
        catch (IOException e)
        {
            return "";
        }
    }

    public static String fileToString (File input)
    {
        try (FileInputStream fis = new FileInputStream (input))
        {
            return streamToString (fis);
        }
        catch (IOException e)
        {
            return "";
        }
    }
}