package HeapAnalyzer.oql;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

public class OqlEngine {

    public static ScriptEngine createEngin(Heap heap) {
        ScriptEngineManager engineManager = new ScriptEngineManager();
        ScriptEngine engine = engineManager.getEngineByName("nashorn");

        engine.put("ctx", new OqlContext(heap));
        engine.setBindings(engine.createBindings(), ScriptContext.GLOBAL_SCOPE);

        return engine;
    }

    public static void eval(Heap heap, String script) throws ScriptException {
        ScriptEngine engine = createEngin(heap);

        engine.eval(script);
    }

    public static class OqlContext {
        public static Class<?> objArray = null;
        public static Method valuesMethod;

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
