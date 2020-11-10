/*
 * Copyright 2020 Mirko Sertic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mirkosertic.flightrecorderstarter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@RestControllerEndpoint(id="flightrecorder")
public class FlightRecorderEndpoint {

    private final static Logger LOGGER = Logger.getLogger(FlightRecorder.class.getCanonicalName());
    private final static MediaType TEXT_CSS = new MediaType("text","css");
    private final static MediaType TEXT_JAVASCRIPT = new MediaType("text","javascript");

    private final ApplicationContext applicationContext;
    private final FlightRecorder flightRecorder;

    public FlightRecorderEndpoint(
            final ApplicationContext applicationContext,
            final FlightRecorder flightRecorder) {
        this.applicationContext = applicationContext;
        this.flightRecorder = flightRecorder;
    }

    private String findBootClass() {
        final Map<String, Object> annotatedBeans = applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
        return annotatedBeans.isEmpty() ? null : annotatedBeans.values().toArray()[0].getClass().getName();
    }

    @GetMapping("/")
    public @ResponseBody ResponseEntity allSessions() {
        try {
            LOGGER.log(Level.INFO, "Retrieving all known recording sessions");
            final List<FlightRecorderPublicSession> sessions = flightRecorder.sessions();
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(sessions);
        } catch (final Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/")
    public @ResponseBody ResponseEntity startRecording(@RequestBody final StartRecordingCommand command) {
        try {
            LOGGER.log(Level.INFO, "Trying to start recording for {0} {1}", new Object[] {command.getDuration(), command.getTimeUnit()});
            final long recordingId = flightRecorder.startRecordingFor(Duration.of(command.getDuration(), command.getTimeUnit()), "");
            LOGGER.log(Level.INFO, "Created recording with ID {0}", recordingId);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(Long.toString(recordingId));
        } catch (final Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{recordingId}")
    public @ResponseBody ResponseEntity closeRecording(@PathVariable final long recordingId) {
        try {
            LOGGER.log(Level.INFO, "Closing recording with ID {0}", recordingId);
            flightRecorder.stopRecording(recordingId);
            return ResponseEntity.ok().build();
        } catch (final Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{recordingId}/flamegraph.html")
    public @ResponseBody ResponseEntity downloadRecordingFlameGraph(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("/flamegraph.html"));
    }

    @GetMapping("/{recordingId}/rawflamegraph.html")
    public @ResponseBody ResponseEntity downloadRecordingRawFlameGraph(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("/rawflamegraph.html"));
    }

    @GetMapping("/{recordingId}/data.json")
    public @ResponseBody ResponseEntity downloadRecordingJson(@PathVariable final long recordingId) {

        LOGGER.log(Level.INFO, "Closing recording with ID {0} and downloading file", recordingId);
        final File file = flightRecorder.stopRecording(recordingId);

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        final ObjectMapper mapper = new ObjectMapper();
        try {

            final String bootClass = findBootClass();
            final FlameGraph graph;
            if (bootClass == null) {
                graph = FlameGraph.from(file);
            } else {
                final int p = bootClass.lastIndexOf(".");
                final String basePackage = bootClass.substring(0, p + 1);
                graph = FlameGraph.from(file, new FlameGraph.PackageNamePrefixFrameFilter(basePackage));
            }
            final String jsonData = mapper.writeValueAsString(graph.getRoot());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);

        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "Could not create json data for flight recording", e);
            return ResponseEntity.badRequest()
                .body(e.getMessage());
        }
    }

    @GetMapping("/{recordingId}/rawdata.json")
    public @ResponseBody ResponseEntity downloadRecordingRawJson(@PathVariable final long recordingId) {

        LOGGER.log(Level.INFO, "Closing recording with ID {0} and downloading file", recordingId);
        final File file = flightRecorder.stopRecording(recordingId);

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        final ObjectMapper mapper = new ObjectMapper();
        try {
            final FlameGraph graph = FlameGraph.from(file);
            final String jsonData = mapper.writeValueAsString(graph.getRoot());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonData);

        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "Could not create json data for flight recording", e);
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    @GetMapping("/{recordingId}/d3.v4.min.js")
    public @ResponseBody ResponseEntity downloadRecording2(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(TEXT_JAVASCRIPT)
                .body(new ClassPathResource("/d3.v4.min.js"));
    }

    @GetMapping("/{recordingId}/d3-flamegraph.min.js")
    public @ResponseBody ResponseEntity downloadRecording3(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(TEXT_JAVASCRIPT)
                .body(new ClassPathResource("/d3-flamegraph.min.js"));
    }

    @GetMapping("/{recordingId}/d3-flamegraph-colorMapper.min.js")
    public @ResponseBody ResponseEntity downloadRecording4(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(TEXT_JAVASCRIPT)
                .body(new ClassPathResource("/d3-flamegraph-colorMapper.min.js"));
    }

    @GetMapping("/{recordingId}/d3-flamegraph-tooltip.min.js")
    public @ResponseBody ResponseEntity downloadRecording5(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(TEXT_JAVASCRIPT)
                .body(new ClassPathResource("/d3-flamegraph-tooltip.min.js"));
    }

    @GetMapping("/{recordingId}/d3-flamegraph.css")
    public @ResponseBody ResponseEntity downloadRecording6(@PathVariable final long recordingId) {

        final HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(TEXT_CSS)
                .body(new ClassPathResource("/d3-flamegraph.css"));
    }

    @GetMapping("/{recordingId}")
    public @ResponseBody ResponseEntity downloadRecording(@PathVariable final long recordingId) {
        LOGGER.log(Level.INFO, "Closing recording with ID {0} and downloading file", recordingId);
        final File file = flightRecorder.stopRecording(recordingId);
        if (file != null) {
            final HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=flightrecording_" + recordingId + ".jfr");
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new FileSystemResource(file));
        }
        return ResponseEntity.notFound().build();
    }
}