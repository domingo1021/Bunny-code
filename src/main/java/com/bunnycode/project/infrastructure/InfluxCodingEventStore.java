package com.bunnycode.project.infrastructure;

import com.bunnycode.project.application.CodingEventStore;
import com.bunnycode.project.application.EventFormat;
import com.bunnycode.project.application.RecordedEvent;
import com.bunnycode.project.application.RecordingProperties;
import com.bunnycode.project.domain.CodingEvent;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * Stores events in the series layout v1 used (measurement = project id, tags version/file/action)
 * so old and new recordings are read by the same query.
 *
 * <p>Differences from v1, all deliberate:
 * <ul>
 *   <li>Points are built with {@link Point}, which escapes values — v1 concatenated raw user code
 *       into line protocol.</li>
 *   <li>Line and column are <em>fields</em> ({@code line_number}, {@code column_index}), not tags:
 *       as tags they created a new series for every cursor position. Distinct names avoid a
 *       type collision with v1's string tags when the two are read together.</li>
 *   <li>A {@code schema=2} tag marks new points so readers can tell the encodings apart.</li>
 *   <li>Code is stored with line breaks escaped ({@link LineBreakEscaping}), because InfluxDB's
 *       CSV query output turns {@code \n} into {@code \r\n}.</li>
 *   <li>Point time is the event's millisecond plus its position in the batch (in nanoseconds), so
 *       two events in the same millisecond never overwrite each other.</li>
 *   <li>The Flux query only interpolates numbers and RFC 3339 instants built by this class, never
 *       caller-provided text.</li>
 * </ul>
 */
@Component
class InfluxCodingEventStore implements CodingEventStore {

    static final String SCHEMA_TAG = "schema";
    static final String CURRENT_SCHEMA = "2";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final InfluxDBClient client;
    private final RecordingProperties.Influx influx;

    InfluxCodingEventStore(InfluxDBClient client, RecordingProperties properties) {
        this.client = client;
        this.influx = properties.influx();
    }

    @Override
    public void append(long projectId, long versionId, long fileId, List<CodingEvent> events) {
        if (events.size() >= NANOS_PER_MILLI) {
            throw new IllegalArgumentException("Too many events in one batch: " + events.size());
        }
        List<Point> points = IntStream.range(0, events.size())
                .mapToObj(sequence -> toPoint(projectId, versionId, fileId, events.get(sequence), sequence))
                .toList();
        client.getWriteApiBlocking().writePoints(influx.bucket(), influx.org(), points);
    }

    @Override
    public List<RecordedEvent> read(long projectId, long versionId, Instant from, Instant to) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "%d" and r.version == "%d")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> group()
                  |> sort(columns: ["_time"])
                """.formatted(influx.bucket(), from, to, projectId, versionId);
        List<RecordedEvent> events = new ArrayList<>();
        client.getQueryApi().query(flux, influx.org())
                .forEach(table -> table.getRecords().forEach(record -> events.add(toEvent(record))));
        return events;
    }

    private static Point toPoint(long projectId, long versionId, long fileId, CodingEvent event, int sequence) {
        long time = event.timestamp().toEpochMilli() * NANOS_PER_MILLI + sequence;
        return Point.measurement(String.valueOf(projectId))
                .addTag("version", String.valueOf(versionId))
                .addTag("file", String.valueOf(fileId))
                .addTag("action", event.action())
                .addTag(SCHEMA_TAG, CURRENT_SCHEMA)
                .addField("code", LineBreakEscaping.escape(event.code()))
                .addField("line_number", event.line())
                .addField("column_index", event.index())
                .time(time, WritePrecision.NS);
    }

    private static RecordedEvent toEvent(FluxRecord record) {
        boolean current = CURRENT_SCHEMA.equals(record.getValueByKey(SCHEMA_TAG));
        int line = current ? asInt(record.getValueByKey("line_number")) : asInt(record.getValueByKey("line"));
        int index = current ? asInt(record.getValueByKey("column_index")) : asInt(record.getValueByKey("index"));
        Object rawCode = record.getValueByKey("code");
        String code = rawCode == null ? "" : rawCode.toString();
        return new RecordedEvent(
                String.valueOf(record.getValueByKey("action")),
                line,
                index,
                current ? LineBreakEscaping.unescape(code) : code,
                Objects.requireNonNull(record.getTime(), "_time"),
                current ? EventFormat.V2 : EventFormat.LEGACY);
    }

    /** v1 stored line/index as string tags; v2 as integer fields. Unreadable values become 0. */
    private static int asInt(Object value) {
        return switch (value) {
            case Number number -> number.intValue();
            case String text -> parseOrZero(text);
            case null, default -> 0;
        };
    }

    private static int parseOrZero(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
