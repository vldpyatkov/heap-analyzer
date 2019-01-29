package HeapAnalyzer.oql;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import org.netbeans.lib.profiler.heap.Instance;

public class JsInstaces implements Iterable<JsInstance> {
    Iterable<Instance> allInstaces;

    public JsInstaces(Iterable<Instance> allInstaces) {
        this.allInstaces = allInstaces;
    }

    @Override public Iterator<JsInstance> iterator() {
        return new JsInstaceIterator(allInstaces.iterator());
    }

    @Override public void forEach(Consumer<? super JsInstance> action) {
        throw new UnsupportedOperationException("forEach");
    }

    @Override public Spliterator<JsInstance> spliterator() {
        throw new UnsupportedOperationException("spliterator");
    }
}
