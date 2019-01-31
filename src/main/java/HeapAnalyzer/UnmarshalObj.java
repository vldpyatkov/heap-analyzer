package HeapAnalyzer;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.ignite.marshaller.jdk.JdkMarshaller;

public class UnmarshalObj {
    static JdkMarshaller marshaller;

    static {
        marshaller = new JdkMarshaller();
    }

    public static void main(String[] args) throws Exception {
        String filePath = args[0];

        byte[] bytes = Files.readAllBytes(Paths.get(filePath));

        Object o = marshaller.unmarshal(bytes, ClassLoader.getSystemClassLoader());

        System.out.println(o.getClass().getName() + "\n" + ReflectionToStringBuilder.toString(o, ToStringStyle.JSON_STYLE));
    }
}
