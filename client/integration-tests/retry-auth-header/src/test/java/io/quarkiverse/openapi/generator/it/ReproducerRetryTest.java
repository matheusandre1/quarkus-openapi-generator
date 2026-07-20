package io.quarkiverse.openapi.generator.it;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ReproducerRetryTest {

    @Test
    void apiClassIsGenerated() {
        Class<?> apiClass = org.acme.reproducer.api.VehiclesApi.class;
        org.junit.jupiter.api.Assertions.assertNotNull(apiClass, "VehiclesApi must be generated");
        Class<?> authProviderClass = org.acme.reproducer.api.auth.CompositeAuthenticationProvider.class;
        org.junit.jupiter.api.Assertions.assertNotNull(authProviderClass, "CompositeAuthenticationProvider must be generated");
    }
}
