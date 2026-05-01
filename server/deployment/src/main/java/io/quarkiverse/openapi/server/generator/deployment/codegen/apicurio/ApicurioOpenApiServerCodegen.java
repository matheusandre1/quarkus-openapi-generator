package io.quarkiverse.openapi.server.generator.deployment.codegen.apicurio;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.openapi.server.generator.deployment.CodegenConfig;
import io.quarkiverse.openapi.server.generator.deployment.ServerCodegenConfig;
import io.quarkiverse.openapi.server.generator.deployment.codegen.ServerCodegenConfigResolver;
import io.quarkiverse.openapi.server.generator.deployment.codegen.ServerCodegenSpec;
import io.quarkus.bootstrap.prebuild.CodeGenException;
import io.quarkus.deployment.CodeGenContext;
import io.quarkus.deployment.CodeGenProvider;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

public class ApicurioOpenApiServerCodegen implements CodeGenProvider {

    private static final Logger log = LoggerFactory.getLogger(ApicurioOpenApiServerCodegen.class);
    private final ServerCodegenConfigResolver configResolver = new ServerCodegenConfigResolver();

    @Override
    public String providerId() {
        return "jaxrs";
    }

    @Override
    public String[] inputExtensions() {
        return new String[] { "json", "yaml", "yml" };
    }

    @Override
    public String inputDirectory() {
        return "resources";
    }

    @Override
    public boolean shouldRun(Path sourceDir, Config config) {

        String serverCodegen = config.getOptionalValue(CodegenConfig.getServerUse(), String.class)
                .orElse(ServerCodegenConfig.APICURIO);
        if (!serverCodegen.equalsIgnoreCase(ServerCodegenConfig.APICURIO)) {
            return false;
        }
        log.info("Generating server code using: [{}]", serverCodegen);

        return configResolver.hasConfiguration(config);
    }

    @Override
    public boolean trigger(CodeGenContext context) throws CodeGenException {
        List<ServerCodegenSpec> specs = configResolver.resolveSpecs(context.inputDir(), context.config());
        for (ServerCodegenSpec spec : specs) {
            File openApiResource = spec.specPath().toFile();
            validateOpenApiResource(openApiResource);

            String specFileName = openApiResource.getName();
            if (Arrays.stream(this.inputExtensions()).noneMatch(specFileName::endsWith)) {
                throw new CodeGenException(
                        "Specification file must have one of the following extensions: " + Arrays.toString(
                                this.inputExtensions()));
            }

            File jsonSpec = specFileName.endsWith("json") ? openApiResource : resolveToJSON(openApiResource.toPath());
            new ApicurioCodegenWrapper(context.outDir().toFile(), spec).generate(jsonSpec.toPath());
        }
        return true;
    }

    private static void validateOpenApiResource(File openApiResource) throws CodeGenException {
        if (!openApiResource.exists()) {
            throw new CodeGenException("Specification file not found: " + openApiResource.getAbsolutePath());
        }
        if (!openApiResource.isFile()) {
            throw new CodeGenException("Specification file is not a file: " + openApiResource.getAbsolutePath());
        }
        if (!openApiResource.canRead()) {
            throw new CodeGenException("Specification file is not readable: " + openApiResource.getAbsolutePath());
        }
    }

    private File resolveToJSON(Path specPath) throws CodeGenException {
        try {
            SwaggerParseResult parseResult = parseAndResolve(specPath);
            OpenAPI openAPI = parseResult.getOpenAPI();
            if (openAPI == null) {
                throw new CodeGenException("Error parsing OpenAPI spec: " + parseMessages(parseResult));
            }

            File jsonFile = File.createTempFile(specPath.getFileName().toString(), ".json");
            jsonFile.deleteOnExit();
            Json.mapper().writeValue(jsonFile, openAPI);
            return jsonFile;
        } catch (Exception e) {
            throw new CodeGenException("Error resolving OpenAPI spec to JSON", e);
        }
    }

    private SwaggerParseResult parseAndResolve(Path specPath) throws CodeGenException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);
        options.setResolveCombinators(true);

        SwaggerParseResult parseResult = new OpenAPIV3Parser()
                .readLocation(specPath.toUri().toString(), null, options);

        if (parseResult == null) {
            throw new CodeGenException("OpenAPI parser returned no result for: " + specPath);
        }
        if (parseResult.getMessages() != null && !parseResult.getMessages().isEmpty()) {
            log.warn("OpenAPI parse warnings for {}: {}", specPath, String.join("; ", parseResult.getMessages()));
        }
        return parseResult;
    }

    private static String parseMessages(SwaggerParseResult parseResult) {
        List<String> messages = parseResult.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "Unknown parsing error";
        }
        return String.join("; ", messages);
    }
}
