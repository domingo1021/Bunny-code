package com.bunnycode.bdd;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every {@code .feature} under {@code src/test/resources/features} against the whole
 * application (random port, real HTTP and STOMP) backed by Testcontainers. Part of
 * {@code ./mvnw verify}; reports land in {@code target/cucumber-reports}.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.bunnycode.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, summary, html:target/cucumber-reports/cucumber.html, junit:target/cucumber-reports/cucumber.xml")
class RunCucumberIT {
}
