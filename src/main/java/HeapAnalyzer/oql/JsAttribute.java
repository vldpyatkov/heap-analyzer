package HeapAnalyzer.oql;

public class JsAttribute {
    public String field;
    public String type;
    public String value;

    public JsAttribute(String field, String type, String value) {
        this.field = field;
        this.type = type;
        this.value = value;
    }

    @Override public String toString() {
        return "JsAttribute{" +
            "field='" + field + '\'' +
            ", type='" + type + '\'' +
            ", value='" + value + '\'' +
            '}';
    }
}
