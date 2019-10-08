package org.jboss.ejb.protocol.remote.tracing;

import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;

public class SpanFormat {
    public static final Format<TextMap> EJB = new Format<TextMap>() {};
}
