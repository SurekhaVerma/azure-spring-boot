/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.azure.spring.autoconfigure.storage;

import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.telemetry.TelemetryData;
import com.microsoft.azure.telemetry.TelemetryProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.util.HashMap;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@Configuration
@ConditionalOnClass(ServiceURL.class)
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "azure.storage", value = {"account-name", "account-key"})
public class StorageAutoConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(StorageAutoConfiguration.class);
    private static final String BLOB_URL = "http://%s.blob.core.windows.net";
    private static final String BLOB_HTTPS_URL = "https://%s.blob.core.windows.net";
    private static final String USER_AGENT_PREFIX = "spring-storage/";

    private final StorageProperties properties;
    private final TelemetryProxy telemetryProxy;

    public StorageAutoConfiguration(StorageProperties properties) {
        this.properties = properties;
        this.telemetryProxy = new TelemetryProxy(properties.isAllowTelemetry());
    }

    /**
     * @param options PipelineOptions bean, not required.
     * @return
     */
    @Bean
    public ServiceURL createServiceUrl(@Autowired(required = false) PipelineOptions options) throws InvalidKeyException,
            MalformedURLException {
        LOG.debug("Creating ServiceURL bean...");
        trackCustomEvent();
        final SharedKeyCredentials credentials = new SharedKeyCredentials(properties.getAccountName(),
                properties.getAccountKey());
        final URL blobUrl = getURL();
        final PipelineOptions pipelineOptions = buildOptions(options);
        final ServiceURL serviceURL = new ServiceURL(blobUrl, StorageURL.createPipeline(credentials, pipelineOptions));

        return serviceURL;
    }

    private URL getURL() throws MalformedURLException {
        if (properties.isEnableHttps()) {
            return new URL(String.format(BLOB_HTTPS_URL, properties.getAccountName()));
        }
        return new URL(String.format(BLOB_URL, properties.getAccountName()));
    }

    private PipelineOptions buildOptions(PipelineOptions fromOptions) {
        final PipelineOptions pipelineOptions = fromOptions == null ? new PipelineOptions() : fromOptions;

        pipelineOptions.withTelemetryOptions(new TelemetryOptions(USER_AGENT_PREFIX
                + pipelineOptions.telemetryOptions().userAgentPrefix()));

        return pipelineOptions;
    }

    @Bean
    @ConditionalOnProperty(prefix = "azure.storage", value = "container-name")
    public ContainerURL createContainerURL(ServiceURL serviceURL) {
        return serviceURL.createContainerURL(properties.getContainerName());
    }

    private void trackCustomEvent() {
        final HashMap<String, String> events = new HashMap<>();

        events.put(TelemetryData.SERVICE_NAME, getClass().getPackage().getName().replaceAll("\\w+\\.", ""));
        events.put(TelemetryData.HASHED_ACCOUNT_NAME, sha256Hex(properties.getAccountName()));

        telemetryProxy.trackEvent(ClassUtils.getUserClass(this.getClass()).getSimpleName(), events);
    }
}
