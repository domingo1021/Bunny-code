package com.bunnycode.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.project.application.EventFormat;
import com.bunnycode.project.application.RecordedEvent;
import com.bunnycode.project.application.RecordingProperties;
import com.bunnycode.project.domain.CodingEvent;
import com.bunnycode.support.TestContainers;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.domain.WritePrecision;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Coding events in InfluxDB")
class InfluxCodingEventStoreIT {

    private static InfluxDBClient client;
    private static InfluxCodingEventStore store;
    private static final Instant T0 = Instant.parse("2026-10-14T10:00:00Z");

    @BeforeAll
    static void connect() {
        var influx = TestContainers.influx();
        var properties = new RecordingProperties(new RecordingProperties.Influx(influx.getUrl(), TestContainers.INFLUX_TOKEN,
                TestContainers.INFLUX_ORG, TestContainers.INFLUX_BUCKET), 50_000);
        client = InfluxDBClientFactory.create(influx.getUrl(), TestContainers.INFLUX_TOKEN.toCharArray(),
                TestContainers.INFLUX_ORG, TestContainers.INFLUX_BUCKET);
        store = new InfluxCodingEventStore(client, properties);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @Test
    @DisplayName("events round-trip exactly and in order — several in one millisecond, quotes, backslashes and line breaks (InfluxDB itself turns \\n into \\r\\n)")
    void roundTrip() {
        List<CodingEvent> events = List.of(
                new CodingEvent("create", 0, 0, "\"", T0),
                new CodingEvent("create", 0, 1, "\"\"", T0),
                new CodingEvent("create", 0, 2, "a,b=c d\\", T0),
                new CodingEvent("enter", 1, 0, "\n", T0.plusMillis(5)),
                new CodingEvent("create", 1, 0, "a\\nb\r\n", T0.plusMillis(6)));

        store.append(101, 1, 7, events);
        List<RecordedEvent> read = store.read(101, 1, T0.minusSeconds(1), T0.plusSeconds(1));

        assertThat(read).extracting(RecordedEvent::code).containsExactly("\"", "\"\"", "a,b=c d\\", "\n", "a\\nb\r\n");
        assertThat(read).extracting(RecordedEvent::line).containsExactly(0, 0, 0, 1, 1);
        assertThat(read).extracting(RecordedEvent::index).containsExactly(0, 1, 2, 0, 0);
        assertThat(read).allSatisfy(event -> assertThat(event.format()).isEqualTo(EventFormat.V2));
    }

    @Test
    @DisplayName("only the requested project and version are returned")
    void isolation() {
        store.append(202, 1, 1, List.of(new CodingEvent("create", 0, 0, "a", T0)));
        store.append(202, 2, 1, List.of(new CodingEvent("create", 0, 0, "b", T0)));
        store.append(203, 1, 1, List.of(new CodingEvent("create", 0, 0, "c", T0)));

        assertThat(store.read(202, 2, T0.minusSeconds(1), T0.plusSeconds(1))).extracting(RecordedEvent::code)
                .containsExactly("b");
    }

    @Test
    @DisplayName("events written by v1 (line/index as tags, no schema tag) are still readable and flagged LEGACY")
    void legacyEvents() {
        long nanos = T0.toEpochMilli() * 1_000_000L;
        client.getWriteApiBlocking().writeRecord(TestContainers.INFLUX_BUCKET, TestContainers.INFLUX_ORG, WritePrecision.NS,
                "303,version=4,file=9,action=create,line=2,index=5 code=\"x\" " + nanos);

        assertThat(store.read(303, 4, T0.minusSeconds(1), T0.plusSeconds(1))).singleElement().satisfies(event -> {
            assertThat(event.format()).isEqualTo(EventFormat.LEGACY);
            assertThat(event.action()).isEqualTo("create");
            assertThat(event.line()).isEqualTo(2);
            assertThat(event.index()).isEqualTo(5);
            assertThat(event.code()).isEqualTo("x");
            assertThat(event.timestamp()).isEqualTo(T0);
        });
    }
}
