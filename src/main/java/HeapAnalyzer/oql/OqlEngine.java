package HeapAnalyzer.oql;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.marshaller.jdk.JdkMarshaller;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

public class OqlEngine {

    static ScriptEngine engine;

    public static ScriptEngine createEngin(Heap heap) {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("nashorn");

        engine.put("ctx", new OqlContext(heap));
        engine.setBindings(engine.createBindings(), ScriptContext.GLOBAL_SCOPE);

        return engine;
    }

    public static void eval(Heap heap, String script) throws ScriptException {
        if (engine == null)
            engine = createEngin(heap);
        else if (script.trim().equalsIgnoreCase("reset")) {
            engine = createEngin(heap);

            return;
        }

        engine.eval(script);
    }

    public static class OqlContext {
        public static Class<?> objArray;
        public static Class<?> primitiveArray;
        public static Method valuesMethod;
        public static Method valuesMethodPrimitive;
        public static Method typeMethod;
        public static JdkMarshaller jdkMarshaller;

        // basic type
        public static final byte OBJECT = 2;
        public static final byte BOOLEAN = 4;
        public static final byte CHAR = 5;
        public static final byte FLOAT = 6;
        public static final byte DOUBLE = 7;
        public static final byte BYTE = 8;
        public static final byte SHORT = 9;
        public static final byte INT = 10;
        public static final byte LONG = 11;

        public static Map<Byte, String> primitiveTypeMap;

        static {
            try {
                objArray = Class.forName("org.netbeans.lib.profiler.heap.ObjectArrayDump");
                valuesMethod = objArray.getMethod("getValues");
                valuesMethod.setAccessible(true);

                primitiveArray = Class.forName("org.netbeans.lib.profiler.heap.PrimitiveArrayDump");
                valuesMethodPrimitive = primitiveArray.getMethod("getValues");
                valuesMethodPrimitive.setAccessible(true);

                typeMethod = primitiveArray.getDeclaredMethod("getType");
                typeMethod.setAccessible(true);

                try {
                    jdkMarshaller = new JdkMarshaller();
                } catch (Throwable e) {
                    System.out.println("Marshaller does not initialized."
                        + "Restart application in oreder to ignite-core.jar to be in classpath,"
                        +" for availability to deserialize byte[].");

                    e.printStackTrace();
                }

                primitiveTypeMap = new HashMap<Byte, String>(10);
                primitiveTypeMap.put(BOOLEAN, "boolean"); //NOI18N
                primitiveTypeMap.put(CHAR, "char"); //NOI18N
                primitiveTypeMap.put(FLOAT, "float"); //NOI18N
                primitiveTypeMap.put(DOUBLE, "double"); //NOI18N
                primitiveTypeMap.put(BYTE, "byte"); //NOI18N
                primitiveTypeMap.put(SHORT, "short"); //NOI18N
                primitiveTypeMap.put(INT, "int"); //NOI18N
                primitiveTypeMap.put(LONG, "long"); //NOI18N
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        private Heap heap;

        public OqlContext(Heap heap) {
            this.heap = heap;
        }

        public JsInstaces all() {
            return new JsInstaces(heap.getAllInstances());
        }

        public JsInstance byId(String id) {
            long instId = Long.valueOf(id);

            return new JsInstance(heap.getInstanceByID(instId));
        }

        public JsInstaces byCls(String cls) {
            return new JsInstaces(heap.getJavaClassByName(cls).getInstances());
        }

        public JsInstaces byRegExpCls(String regExpCls) {
            Collection<JavaClass> clss = heap.getJavaClassesByRegExp(regExpCls);

            if (clss != null && !clss.isEmpty()) {
                for (JavaClass cls1 : clss)
                    return new JsInstaces(cls1.getInstances());
            }

            return new JsInstaces(Collections.<Instance>emptyList());
        }

        public Object deserialize(JsInstance byteArray) throws IgniteCheckedException {
            byte[] ba = new byte[byteArray.attributes.size()];

            for (int i=0; i<ba.length; i++) {
                assert byteArray.attributes.get(i).type.equals("byte") : "Attribute type "
                    + byteArray.attributes.get(i).type + " required byte";

                ba[i] = Byte.valueOf(byteArray.attributes.get(i).value);
            }

            Object result = jdkMarshaller.unmarshal(ba, getClass().getClassLoader());

            System.out.println(ReflectionToStringBuilder.toString(result, ToStringStyle.JSON_STYLE));

            javax.cache.configuration.Factory ff;

            return result;
        }

        public long rSize(JsInstance inst) {
            return rSize(inst, false);
        }

        public long rSize(JsInstance inst, boolean prune) {
            Stack<Instance> stack = new Stack<Instance>();

            LongHashSet marks = new LongHashSet();

            long fullSize = 0;

            long startNodeId = Long.valueOf(inst.id);

            marks.add(startNodeId);

            stack.push(heap.getInstanceByID(startNodeId));

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
                                    if (!prune || startNodeId < objId)
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

                                if (!prune || startNodeId < objId)
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
    }
}
