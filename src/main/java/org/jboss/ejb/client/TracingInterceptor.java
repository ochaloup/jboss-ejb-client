package org.jboss.ejb.client;

import java.net.URI;
import java.util.Collection;

import org.jboss.ejb.client.annotation.ClientInterceptorPriority;

@ClientInterceptorPriority(TracingInterceptor.PRIORITY)
public class TracingInterceptor implements EJBClientInterceptor {

    public static final int PRIORITY = ClientInterceptorPriority.JBOSS_AFTER + 10_000; 
    
    @Override
    public void handleInvocation(EJBClientInvocationContext context) throws Exception {
        Collection<URI> attachment = context.getAttachment(TransactionInterceptor.PREFERRED_DESTINATIONS);
        System.out.println(attachment);
        context.sendRequest();
//        SpanContext spanContext = (SpanContext) value;
//        try {
//            Object txn = transactionSupplier.get().getTransaction();
//            System.out.println(txn);
//            Tracing.activateSpan(new Tracing.DefaultSpanBuilder(SpanName.SUBORD_ROOT)
//                    .buildSubordinateIfAbsent("???????", spanContext));
//        } catch (SystemException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public Object handleInvocationResult(EJBClientInvocationContext context) throws Exception {
        return context.getResult();
    }
}
