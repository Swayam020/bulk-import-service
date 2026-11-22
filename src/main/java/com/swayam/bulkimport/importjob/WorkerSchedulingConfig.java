package com.swayam.bulkimport.importjob;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.import.worker.enabled", havingValue = "true", matchIfMissing = true)
class WorkerSchedulingConfig {
}
