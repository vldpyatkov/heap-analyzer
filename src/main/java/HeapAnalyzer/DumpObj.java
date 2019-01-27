package HeapAnalyzer;

import HeapAnalyzer.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

public class DumpObj {

    public static Class<?> objArray = null;
    public static Method valuesMethod;

    private static Options cliOps;
    private static CommandLineParser parser;

    static {
        try {
            objArray = Class.forName("org.netbeans.lib.profiler.heap.ObjectArrayDump");
            valuesMethod = objArray.getMethod("getValues");
            valuesMethod.setAccessible(true);
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        cliOps = new Options()
            .addOption("h", "hprof", true, "Java heap dump file.")
            .addOption("id", "object-id", true, "Object id as decimal long.")
            .addOption("pn", "property-name", true, "Name of property (using with id).")
            .addOption("c", "class-name", true, "Full class name.");

        parser = new DefaultParser();
    }

    private static void printHelp(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        final String syntax = "Main";
        final String usageHeader = "Heap analyzer";
        final String usageFooter = "See http://marxsoftware.blogspot.com/ for further details.";
        log("====HELP====");
        formatter.printHelp(syntax, usageHeader, options, usageFooter);
    }

    public static void main(String[] args) throws Exception {
        try {
            CommandLine cmd = parser.parse(cliOps, args);

            String dumppath = cmd.getOptionValue("h");

            Heap heap = load(dumppath);

            log("Heap loaded. Searching for biggest classes...");

            if (cmd.hasOption("id")) {
                long objId = Long.valueOf(cmd.getOptionValue("id"));

                String propName = cmd.getOptionValue("pn");

                log("Claculating size of object " + objId + " (" + Long.toHexString(objId) + ") proprty " + propName);

                printInstanceInfoById(heap, objId, propName);
            }
            else {
                String className = cmd.getOptionValue("c");

                log("Claculating size of all instances " + className);

                JavaClass jClass = heap.getJavaClassByName(className);

                if (jClass != null) {
                    for (Instance inst : jClass.getInstances()) {
                        log("Instance id = " + inst.getInstanceId()
                            + " (" + Long.toHexString(inst.getInstanceId()) + ")"
                            + " retained size = " + FileUtils.byteCountToDisplaySize(reteinedSize(heap, inst)));
                    }
                }
                else
                    log("Class not found.");
            }

        }
        catch (Exception e) {
            log("Some exception is occured: " + e.getMessage());
            e.printStackTrace();

            printHelp(cliOps);
        }
    }

    private static void printInstanceInfoById(Heap heap, long id, String propName) {
        Instance attrInst = heap.getInstanceByID(id);

        if (attrInst != null) {
            log("Found instance of " + attrInst.getJavaClass().getName());

            logInstanceInfo(heap, attrInst, propName);
        }
        else
            log("No istace found.");
    }

    private static void logInstanceInfo(Heap heap, Instance inst, String propName) {
        int propCnt = 0;

        for (FieldValue fVal : inst.getFieldValues()) {
            if ("*".equals(propName) ||
                fVal.getField().getName().trim().equalsIgnoreCase(propName)) {
                log("Filed " + fVal.getField().getName());

                propCnt++;

                if (isTypeOf(fVal, "object")
                    && !fVal.getValue().trim().equals("0")) {
                    long objId = Long.valueOf(fVal.getValue().trim());

                    Instance propInst = heap.getInstanceByID(objId);

                    log("Instance id = " + propInst.getInstanceId()
                        + " (" + Long.toHexString(propInst.getInstanceId()) + ")"
                        + " cls = " + propInst.getJavaClass().getName()
                        + " retained size = " + FileUtils.byteCountToDisplaySize(reteinedSize(heap, propInst)));
                }
                else if (isTypeOf(fVal, "object")
                    && fVal.getValue().trim().equals("0"))
                    log("Instance is null.");
                else if (isTypeOf(fVal, "byte"))
                    log("Byte primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "int"))
                    log("Integer primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "long"))
                    log("Long primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "float"))
                    log("Float primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "double"))
                    log("Double primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "boolean"))
                    log("Boolean primitive val = " + fVal.getValue());
                else if (isTypeOf(fVal, "char"))
                    log("Char primitive val = " + fVal.getValue());
            }
        }

        if (propCnt == 0)
            log("No property found.");
    }

    private static long reteinedSize(Heap heap, Instance inst) {
        Stack<Instance> stack = new Stack<Instance>();

        LongHashSet marks = new LongHashSet();

        long fullSize = 0;

        marks.add(inst.getInstanceId());

        stack.push(inst);

        while (!stack.empty()) {
            Instance inst2 = stack.pop();

            fullSize += inst2.getSize();

            if (inst2.getClass() == objArray) {
                try {
                    List<Instance> arrInstances = (List<Instance>)valuesMethod.invoke(inst2);

                    for (Instance arrInstance : arrInstances) {
                        if (arrInstance != null) {

                            long objId = arrInstance.getInstanceId();

                            if (!marks.contains(objId)) {
                                stack.push(arrInstance);

                                marks.add(objId);
                            }
                        }
                    }
                }
                catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            else
                for (FieldValue fVal : inst2.getFieldValues()) {

                    if (isTypeOf(fVal, "object")
                        && !fVal.getValue().trim().equals("0")) {

                        long objId = Long.valueOf(fVal.getValue().trim());

                        if (!marks.contains(objId)) {
                            Instance fInst = heap.getInstanceByID(objId);

                            stack.push(fInst);

                            marks.add(objId);
                        }
                    }
                }
        }

        return fullSize;
    }

    private static boolean isTypeOf(FieldValue fVal, String type) {
        return fVal.getField().getType().getName().equals(type)
            && fVal.getValue() != null;
    }

    public static void log(String text) {
        System.out.println(new Date() + " " + text);
    }

    public static Heap load(String filename) throws IOException {
        File sFile = new File(filename);
        if (!sFile.exists())
            throw new IllegalArgumentException("File " + filename + " doesn't exist!");
        if (!sFile.isFile())
            throw new IllegalArgumentException("File " + filename + " not a regular file!");
        log("Loading " + filename + "...");
        return HeapFactory.createFastHeap(sFile);
    }
}
