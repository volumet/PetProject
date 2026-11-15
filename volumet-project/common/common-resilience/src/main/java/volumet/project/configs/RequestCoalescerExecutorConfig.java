package volumet.project.configs;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import volumet.project.components.RequestCoalescer;
import volumet.project.configs.properties.RequestCoalescerExecutorProperties;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Autoconfiguration (common-resilience): request coalescer and its virtual-thread executor.
 * Settings: volumet.executor.request-coalescer.*. Each bean backs off if the app defines its own.
 */
@AutoConfiguration
@EnableConfigurationProperties(RequestCoalescerExecutorProperties.class)
public class RequestCoalescerExecutorConfig {
    public static final String REQUEST_COALESCER_TASK_EXECUTOR = "requestCoalescerTaskExecutor";

    /**
     * Runs each coalesced load on its own virtual thread (no pool, no queue).
     * When the concurrency limit is reached, the submitting thread blocks until a slot frees up.
     * On shutdown (close()), waits up to awaitTermination for running loads to finish.
     */
    @Bean(name = REQUEST_COALESCER_TASK_EXECUTOR)
    @ConditionalOnMissingBean(name = REQUEST_COALESCER_TASK_EXECUTOR)
    public SimpleAsyncTaskExecutor requestCoalescerTaskExecutor(RequestCoalescerExecutorProperties properties) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(properties.getThreadNamePrefix());
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(properties.getConcurrencyLimit());
        executor.setTaskDecorator(mdcPropagatingDecorator());
        executor.setTaskTerminationTimeout(properties.getAwaitTermination().toMillis());
        executor.setRejectTasksWhenLimitReached(true);
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestCoalescer requestCoalescer(@Qualifier(REQUEST_COALESCER_TASK_EXECUTOR) Executor executor,
                                             RequestCoalescerExecutorProperties properties) {
        return new RequestCoalescer(executor, properties);
    }

    /**
     * Copies the submitting thread's MDC (correlation id) into the worker thread so
     * logs from the coalesced load keep the leader request's correlation id.
     * <p>
     * The outer lambda runs on the submitting (request) thread: only read its MDC there, never clear it.
     * The worker's MDC is cleared in the inner lambda's finally (virtual threads are never reused anyway).
     */
    private static TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
