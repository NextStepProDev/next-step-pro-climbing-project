package pl.nextsteppro.climbing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.setTaskDecorator(runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("Mail task failed unexpectedly — email was NOT sent", e);
            }
        });
        executor.initialize();
        return executor;
    }

    /**
     * Dedicated executor for bulk mail campaigns (admin broadcast, newsletter of an article).
     * <p>Deliberately single-threaded: each campaign is ONE task that loops and sends
     * sequentially, so memory stays flat (one MIME message built/sent/GC'd at a time) and the
     * ~1-core box is never hit by two concurrent SMTP loops. Kept separate from
     * {@link #mailExecutor()} so a large broadcast cannot flood the transactional mail queue
     * (queue=100) — which would both reject tasks past the limit and starve time-sensitive
     * mail (reservation/waitlist confirmations). CallerRuns as the saturation policy so a
     * campaign is never silently dropped if the small queue ever fills.
     */
    @Bean("mailCampaignExecutor")
    public Executor mailCampaignExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("mail-campaign-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("Mail campaign task failed unexpectedly", e);
            }
        });
        executor.initialize();
        return executor;
    }
}
