package HeapAnalyzer.oql;

import java.util.Iterator;
import org.netbeans.lib.profiler.heap.Instance;

public class JsInstaceIterator implements Iterator<JsInstance> {
    private Iterator<Instance> instanceIterator;

    public JsInstaceIterator(Iterator<Instance> instanceIterator) {
        this.instanceIterator = instanceIterator;
    }

    @Override public boolean hasNext() {
        return instanceIterator.hasNext();
    }

    @Override public JsInstance next() {
        Instance inst = instanceIterator.next();

        return new JsInstance(inst);
    }
}
