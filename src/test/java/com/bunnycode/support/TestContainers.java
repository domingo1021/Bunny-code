package com.bunnycode.support;

import java.net.URI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InfluxDBContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * One container per backing service for the whole test JVM, started on first use. They are
 * deliberately not Spring beans: Spring stops container beans when a context closes, which would
 * pull the database out from under other cached contexts. Ryuk removes them when the JVM exits.
 */
public final class TestContainers {

    public static final String DATABASE = "bunny_code";
    public static final String INFLUX_ORG = "bunny";
    public static final String INFLUX_BUCKET = "bunny";
    public static final String INFLUX_TOKEN = "test-influx-admin-token";
    public static final String S3_BUCKET = "bunny-code-test";

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName(DATABASE)
            .withUsername("bunny")
            .withPassword("bunny");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    private static final InfluxDBContainer<?> INFLUX = new InfluxDBContainer<>(DockerImageName.parse("influxdb:2.7-alpine"))
            .withUsername("bunny")
            .withPassword("bunny-password")
            .withOrganization(INFLUX_ORG)
            .withBucket(INFLUX_BUCKET)
            .withAdminToken(INFLUX_TOKEN);

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4")).withServices("s3");

    private static boolean bucketCreated;

    private TestContainers() {
    }

    public static synchronized MySQLContainer mysql() {
        start(MYSQL);
        return MYSQL;
    }

    public static synchronized GenericContainer<?> redis() {
        start(REDIS);
        return REDIS;
    }

    public static synchronized InfluxDBContainer<?> influx() {
        start(INFLUX);
        return INFLUX;
    }

    public static synchronized LocalStackContainer localStack() {
        start(LOCALSTACK);
        if (!bucketCreated) {
            try (S3Client s3 = s3Client()) {
                s3.createBucket(request -> request.bucket(S3_BUCKET));
            }
            bucketCreated = true;
        }
        return LOCALSTACK;
    }

    /** A client for asserting on stored objects. */
    public static S3Client s3Client() {
        start(LOCALSTACK);
        return S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .forcePathStyle(true)
                .build();
    }

    public static String publicBaseUrl() {
        URI endpoint = localStack().getEndpoint();
        return endpoint.toString().replaceAll("/$", "") + "/" + S3_BUCKET;
    }

    private static void start(GenericContainer<?> container) {
        if (!container.isRunning()) {
            container.start();
        }
    }
}
