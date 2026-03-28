package net.pkhapps.idispatchx.gis.server;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(
        packages = "net.pkhapps.idispatchx.gis.server",
        importOptions = ImportOption.DoNotIncludeTests.class)
class GisServerArchitectureTest {

    @ArchTest
    static final ArchRule layeringRules = layeredArchitecture()
            .consideringAllDependencies()
            .withOptionalLayers(true)
            .layer("API Adapters").definedBy("..gis.server.api..")
            .layer("Auth Adapters").definedBy("..gis.server.auth..")
            .layer("Service").definedBy("..gis.server.service..")
            .layer("Repository").definedBy("..gis.server.repository..")
            .layer("Model").definedBy("..gis.server.model..")
            .layer("DB Infrastructure").definedBy("..gis.server.db..")
            // Rule 1: Service must not depend on API adapter or auth adapter layers
            .whereLayer("Service").mayOnlyAccessLayers("Repository", "Model", "DB Infrastructure")
            // Rule 2: API adapters must not depend on the repository layer directly
            .whereLayer("API Adapters").mayOnlyAccessLayers("Service", "Auth Adapters", "Model")
            // Rule 3: Auth adapters must not depend on the service or repository layers
            .whereLayer("Auth Adapters").mayNotAccessAnyLayer()
            // Rule 4: Model layer must not depend on any other layer
            .whereLayer("Model").mayNotAccessAnyLayer();

    @ArchTest
    static final ArchRule noCyclicDependencies = slices()
            .matching("net.pkhapps.idispatchx.gis.server.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);
}
