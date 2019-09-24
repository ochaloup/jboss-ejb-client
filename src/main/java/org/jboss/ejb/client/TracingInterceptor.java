package org.jboss.ejb.client;

import org.jboss.ejb.client.annotation.ClientInterceptorPriority;

import io.opentracing.Span;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

@ClientInterceptorPriority(TracingInterceptor.PRIORITY)
public class TracingInterceptor implements EJBClientInterceptor {

    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 175;

    @Override
    public void handleInvocation(EJBClientInvocationContext context) throws Exception {
        Span span = GlobalTracer.get().activeSpan();
        // EJB client shouldn't be responsible for creating 
        // any spans (if it is not instrumenting the EJB client itself)
        if (span != null) {
            GlobalTracer.get().inject(span.context(), Format.Builtin.TEXT_MAP, new TextMapAdapter(context.getContextData()));
        }
        context.sendRequest();
    }

    @Override
    public Object handleInvocationResult(EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }

    /**
     * Instead of using {@link io.opentracing.propagation.TextMapAdapter}, we need to be
     * able to adapt to the type Map&lt;String, Object&gt; since that is the carrier
     * type for the EJB client.
     */
    public static class TextMapAdapter implements TextMap {

        private final Map<String, ? super String> map;

        public TextMapAdapter(Map<String, ? super String> map) {
            this.map = map;
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }

        @Override
        public Iterator<Map.Entry<String, String>> iterator() {
            return map.entrySet()
                    .stream()
                    .filter(x -> x.getValue() instanceof String)
                    .collect(Collectors.toMap(Map.Entry::getKey, x -> (String) x.getValue()))
                    .entrySet()
                    .iterator();
        }
    }
}
