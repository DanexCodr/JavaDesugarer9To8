package j9compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

/**
 * Minimal Java 8-compatible backport of {@code java.net.http.HttpHeaders}.
 */
public final class HttpHeaders {

    private final Map<String, List<String>> headers;
    private final Map<String, List<String>> lookup;

    private HttpHeaders(Map<String, List<String>> headers,
                        Map<String, List<String>> lookup) {
        this.headers = headers;
        this.lookup = lookup;
    }

    public static HttpHeaders of(Map<String, List<String>> headers,
                                 BiPredicate<String, String> filter) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }
        if (filter == null) {
            throw new NullPointerException("filter");
        }
        Map<String, List<String>> collected = new LinkedHashMap<String, List<String>>();
        Map<String, List<String>> lookup = new HashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String name = entry.getKey();
            if (name == null) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null) {
                continue;
            }
            List<String> accepted = new ArrayList<String>();
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                if (filter.test(name, value)) {
                    accepted.add(value);
                }
            }
            if (!accepted.isEmpty()) {
                List<String> frozen = Collections.unmodifiableList(new ArrayList<String>(accepted));
                collected.put(name, frozen);
                lookup.put(name.toLowerCase(Locale.US), frozen);
            }
        }
        return new HttpHeaders(Collections.unmodifiableMap(collected),
                Collections.unmodifiableMap(lookup));
    }

    public Map<String, List<String>> map() {
        return headers;
    }

    public Optional<String> firstValue(String name) {
        List<String> values = values(name);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.get(0));
    }

    public OptionalLong firstValueAsLong(String name) {
        List<String> values = values(name);
        if (values.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(values.get(0)));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public List<String> allValues(String name) {
        List<String> values = values(name);
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        return values;
    }

    private List<String> values(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        List<String> values = lookup.get(name.toLowerCase(Locale.US));
        return values == null ? Collections.<String>emptyList() : values;
    }
}
