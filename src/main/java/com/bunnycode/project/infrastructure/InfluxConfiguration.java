package com.bunnycode.project.infrastructure;

import com.bunnycode.project.application.RecordingProperties;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InfluxConfiguration {

    @Bean(destroyMethod = "close")
    InfluxDBClient influxDBClient(RecordingProperties properties) {
        RecordingProperties.Influx influx = properties.influx();
        return InfluxDBClientFactory.create(influx.url(), influx.token().toCharArray(), influx.org(), influx.bucket());
    }
}
