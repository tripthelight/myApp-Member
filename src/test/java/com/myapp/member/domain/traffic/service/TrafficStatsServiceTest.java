package com.myapp.member.domain.traffic.service;

import com.myapp.member.domain.traffic.dto.AdminTrafficResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TrafficStatsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readAdminTrafficCalculatesTrafficStatistics() throws Exception {
        String browserUserAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 "
                        + "Chrome/151.0.0.0 Safari/537.36";

        String historyLog = String.join(
                "\n",
                logLine(
                        "203.0.113.10",
                        "23/Aug/2026:12:00:00 +0000",
                        "GET / HTTP/1.1",
                        200,
                        browserUserAgent
                ),
                logLine(
                        "203.0.113.10",
                        "23/Aug/2026:12:00:01 +0000",
                        "GET /assets/app.js HTTP/1.1",
                        200,
                        browserUserAgent
                ),
                logLine(
                        "198.51.100.20",
                        "23/Aug/2026:12:00:02 +0000",
                        "GET /.env HTTP/1.1",
                        404,
                        browserUserAgent
                ),
                logLine(
                        "198.51.100.20",
                        "23/Aug/2026:12:00:03 +0000",
                        "GET / HTTP/1.1",
                        200,
                        browserUserAgent
                ),
                logLine(
                        "198.51.100.20",
                        "23/Aug/2026:12:00:04 +0000",
                        "GET /assets/site.css HTTP/1.1",
                        200,
                        browserUserAgent
                ),
                logLine(
                        "127.0.0.1",
                        "23/Aug/2026:12:00:05 +0000",
                        "GET /healthz HTTP/1.1",
                        200,
                        "Wget"
                )
        ) + "\n";

        String currentLog = String.join(
                "\n",
                logLine(
                        "203.0.113.10",
                        "23/Aug/2026:12:00:06 +0000",
                        "GET /lv13 HTTP/1.1",
                        200,
                        browserUserAgent
                ),
                logLine(
                        "192.0.2.30",
                        "23/Aug/2026:12:00:07 +0000",
                        "GET / HTTP/1.1",
                        200,
                        "curl/8.5.0"
                )
        ) + "\n";

        Files.writeString(
                tempDir.resolve("access-history.log"),
                historyLog,
                StandardCharsets.UTF_8
        );

        Files.writeString(
                tempDir.resolve("access.log"),
                currentLog,
                StandardCharsets.UTF_8
        );

        TrafficStatsService service =
                new TrafficStatsService(tempDir.toString());

        AdminTrafficResponseDTO response =
                service.readAdminTraffic();

        assertNotNull(response.generatedAt());
        assertNotNull(response.periodStart());
        assertNotNull(response.periodEnd());

        assertEquals(7L, response.totalRequests());
        assertEquals(3L, response.externalIpCount());
        assertEquals(2L, response.browserLikeIpCount());
        assertEquals(1L, response.estimatedVisitorCount());

        assertEquals(2L, response.scannerRequestCount());
        assertEquals(2L, response.scannerIpCount());

        assertEquals(1, response.dailyTraffic().size());
        assertEquals(
                7L,
                response.dailyTraffic().get(0).requests()
        );
        assertEquals(
                1L,
                response.dailyTraffic().get(0).estimatedVisitors()
        );

        assertEquals(2, response.popularPages().size());

        assertEquals(
                "/",
                response.popularPages().get(0).path()
        );
        assertEquals(
                1L,
                response.popularPages().get(0).requests()
        );

        assertEquals(
                "/lv13",
                response.popularPages().get(1).path()
        );
        assertEquals(
                1L,
                response.popularPages().get(1).requests()
        );
    }

    private String logLine(
            String ip,
            String timestamp,
            String request,
            int status,
            String userAgent
    ) {
        return ip
                + " - - ["
                + timestamp
                + "] \""
                + request
                + "\" "
                + status
                + " 123 \"-\" \""
                + userAgent
                + "\"";
    }
}