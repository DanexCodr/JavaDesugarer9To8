package desugarer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

final class MethodTransformRegistry {

    private MethodTransformRegistry() {}

    static List<MethodTransform> load() {
        List<MethodTransform> transforms = new ArrayList<>();
        transforms.add(new Java10MethodTransform());
        transforms.add(new Java11MethodTransform());

        ServiceLoader<MethodTransform> loader = ServiceLoader.load(MethodTransform.class);
        for (MethodTransform transform : loader) {
            transforms.add(transform);
        }
        return Collections.unmodifiableList(transforms);
    }
}
