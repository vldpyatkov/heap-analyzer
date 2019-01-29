package HeapAnalyzer.oql;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;

public class JsInstance {
    public long id;
    public long size;
    public String className;
    public List<JsAttribute> attributes;

    public JsInstance(Instance instance) {
        id = instance.getInstanceId();
        size = instance.getSize();
        className = instance.getJavaClass().getName();
        attributes = getAttributes(instance);
    }

    private static boolean isTypeOf(FieldValue fVal, String type) {
        return fVal.getField().getType().getName().equals(type)
            && fVal.getValue() != null;
    }

    private List<JsAttribute> getAttributes(Instance inst) {
        List<JsAttribute> attributes = new ArrayList<>();

        if (inst.getClass() == OqlEngine.OqlContext.objArray) {
            try {
                List<Instance> arrInstances = (List<Instance>)OqlEngine.OqlContext.valuesMethod.invoke(inst);

                int idx = 0;

                for (Instance arrInstance : arrInstances) {
                    attributes.add(new JsAttribute(String.valueOf(idx++), "object", arrInstance == null
                        ? null : String.valueOf(arrInstance.getInstanceId())));
                }
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        else {
            for (FieldValue fVal : inst.getFieldValues()) {
                attributes.add(new JsAttribute(fVal.getField().getName(), fVal.getField().getType().getName(),
                    (fVal.getValue().trim().equals("0") && isTypeOf(fVal, "object")) ? null : fVal.getValue()));
            }
        }

        return attributes;
    }

    @Override public String toString() {
        return "JsInstance{" +
            "id=" + id +
            ", size=" + size +
            ", className='" + className + '\'' +
            ", attributes=" + attributes +
            '}';
    }
}
