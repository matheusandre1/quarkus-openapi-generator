package io.quarkiverse.openapi.server.generator.deployment.codegen.openapitools;

import static io.quarkiverse.openapi.server.generator.deployment.ServerCodegenConfig.APICURIO;
import static io.quarkiverse.openapi.server.generator.deployment.ServerCodegenConfig.OPENAPITOOLS;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import io.quarkiverse.openapi.server.generator.deployment.CodegenConfig;
import io.quarkiverse.openapi.server.generator.deployment.codegen.ServerCodegenConfigResolver;
import io.quarkiverse.openapi.server.generator.deployment.codegen.ServerCodegenSpec;
import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;

public class OpenAPIToolsServerCodegen implements CodeGenProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAPIToolsServerCodegen.class);
    private final ServerCodegenConfigResolver configResolver = new ServerCodegenConfigResolver();

    @Override
    public String providerId() {
        return QuarkusJavaServerCodegen.CODEGEN_NAME;
    }

    @Override
    public String inputDirectory() {
        return "resources";
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        Path outputDir = context.outDir();
        for (ServerCodegenSpec spec : configResolver.resolveSpecs(context.inputDir(), context.config())) {
            File openAPIFile = spec.specPath().toFile();

            LOGGER.info("Generating server side code for: " + openAPIFile);

            QuarkusJavaServerCodegenConfigurator configurator = new QuarkusJavaServerCodegenConfigurator()
                    .withBasePackage(spec.basePackage())
                    .withGenerateBuilders(spec.builders())
                    .withBeanValidation(beanValidation(context, spec))
                    .withReactive(spec.reactive())
                    .withInputBaseDir(openAPIFile.toString())
                    .withOutputDir(outputDir.toAbsolutePath().toString());

            generate(configurator);
        }

        return true;
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {
        String serverCodegen = config.getOptionalValue(CodegenConfig.getServerUse(), String.class)
                .orElse(APICURIO);
        return serverCodegen.equalsIgnoreCase(OPENAPITOOLS) && configResolver.hasConfiguration(config);
    }

    private void generate(QuarkusJavaServerCodegenConfigurator configurator) {
        OpenAPIToolsGenerator generator = new OpenAPIToolsGenerator(configurator);
        List<File> generatedFiles = generator.generate();
        for (File generatedFile : generatedFiles) {
            LOGGER.info("Generated file: " + generatedFile);
        }
    }

    private boolean beanValidation(CodeGenContext context, ServerCodegenSpec spec) {
        if (!spec.beanValidation()) {
            return false;
        }

        boolean hibernateValidatorCapabilityIsPresent = context.applicationModel() != null
                && context.applicationModel().getExtensionCapabilities().stream()
                        .flatMap(extensionCapability -> extensionCapability.getProvidesCapabilities().stream())
                        .anyMatch(Capability.HIBERNATE_VALIDATOR::equals);

        if (!hibernateValidatorCapabilityIsPresent) {
            throw new IllegalStateException(
                    "The extension io.quarkus:quarkus-hibernate-validator is required when the property " +
                            "quarkus.openapi.generator.server.bean-validation is set to true.");
        }

        return true;
    }

}
