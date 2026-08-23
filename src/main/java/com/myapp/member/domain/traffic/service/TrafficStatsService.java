package com.myapp.member.domain.traffic.service;

import com.myapp.member.domain.traffic.dto.AdminTrafficResponseDTO;
import com.myapp.member.domain.traffic.dto.AdminTrafficResponseDTO.DailyTrafficDTO;
import com.myapp.member.domain.traffic.dto.AdminTrafficResponseDTO.PopularPageDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TrafficStatsService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter NGINX_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(
                    "dd/MMM/yyyy:HH:mm:ss Z",
                    Locale.ENGLISH
            );

    private static final Pattern NGINX_LOG_PATTERN = Pattern.compile(
            "^(\\S+)\\s+-\\s+-\\s+\\[([^]]+)]\\s+\"([^\"]*)\"\\s+"
                    + "(\\d{3})\\s+(\\S+)\\s+\"([^\"]*)\"\\s+\"([^\"]*)\".*$"
    );

    private static final Pattern PAGE_PATTERN = Pattern.compile(
            "^/$|^/lv\\d+/?$"
    );

    private static final Pattern ASSET_PATTERN = Pattern.compile(
            "^/assets/.*\\.(?:js|css|svg|png|jpe?g|webp|gif|ico|woff2?|ttf|otf|mp3|wav|ogg)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> LOG_FILE_NAMES = List.of(
            "access-history.log",
            "access.log"
    );

    private static final List<String> SCANNER_PATH_PATTERNS = List.of(
            "/.env",
            "/.git",
            "wp-admin",
            "wp-login",
            "wp-includes",
            "xmlrpc.php",
            "phpinfo",
            "xampp",
            "/x.php",
            "/wp.php",
            "/geoserver",
            "/wsgi.py",
            "phpunit",
            "/cgi-bin/",
            "server-status",
            "/actuator",
            ".php"
    );

    private static final List<String> BOT_USER_AGENT_PATTERNS = List.of(
            "bot",
            "crawler",
            "spider",
            "slurp",
            "headless",
            "curl/",
            "wget",
            "python",
            "go-http-client",
            "masscan",
            "zgrab",
            "nikto",
            "nmap",
            "sqlmap",
            "libwww-perl"
    );

    private static final List<String> BROWSER_USER_AGENT_PATTERNS = List.of(
            "mozilla/",
            "chrome/",
            "crios/",
            "safari/",
            "firefox/",
            "fxios/",
            "edg/",
            "opr/"
    );

    private final Path logDirectory;

    public TrafficStatsService(
            @Value("${NGINX_ACCESS_LOG_DIR:}") String logDirectory
    ) {
        if (logDirectory == null || logDirectory.isBlank()) {
            this.logDirectory = null;
        } else {
            this.logDirectory = Path.of(logDirectory);
        }
    }

    public AdminTrafficResponseDTO readAdminTraffic() {
        List<LogEntry> entries = readLogEntries();

        if (entries.isEmpty()) {
            return emptyResponse();
        }

        Map<String, VisitorState> visitorStates = new HashMap<>();
        Map<LocalDate, DailyState> dailyStates = new HashMap<>();
        Set<String> scannerIps = new HashSet<>();

        long totalRequests = 0L;
        long scannerRequestCount = 0L;

        OffsetDateTime periodStart = null;
        OffsetDateTime periodEnd = null;

        for (LogEntry entry : entries) {
            if (isLocalhost(entry.ip())) {
                continue;
            }

            totalRequests++;

            OffsetDateTime seoulTimestamp =
                    entry.timestamp()
                            .atZoneSameInstant(SEOUL_ZONE)
                            .toOffsetDateTime();

            if (periodStart == null || seoulTimestamp.isBefore(periodStart)) {
                periodStart = seoulTimestamp;
            }

            if (periodEnd == null || seoulTimestamp.isAfter(periodEnd)) {
                periodEnd = seoulTimestamp;
            }

            boolean browserLike = isBrowserLike(entry.userAgent());
            boolean pageRequest = isPagePath(entry.path());
            boolean assetRequest = isAssetPath(entry.path());
            boolean scannerRequest =
                    isScannerPath(entry.path())
                            || isBotUserAgent(entry.userAgent());

            VisitorState visitorState =
                    visitorStates.computeIfAbsent(
                            entry.ip(),
                            ignored -> new VisitorState()
                    );

            if (browserLike) {
                visitorState.browserLike = true;
            }

            if (pageRequest) {
                visitorState.pageRequested = true;
            }

            if (assetRequest) {
                visitorState.assetRequested = true;
            }

            if (scannerRequest) {
                scannerRequestCount++;
                scannerIps.add(entry.ip());
            }

            LocalDate date = seoulTimestamp.toLocalDate();

            DailyState dailyState =
                    dailyStates.computeIfAbsent(
                            date,
                            ignored -> new DailyState()
                    );

            dailyState.requests++;

            VisitorState dailyVisitorState =
                    dailyState.visitors.computeIfAbsent(
                            entry.ip(),
                            ignored -> new VisitorState()
                    );

            if (browserLike) {
                dailyVisitorState.browserLike = true;
            }

            if (pageRequest) {
                dailyVisitorState.pageRequested = true;
            }

            if (assetRequest) {
                dailyVisitorState.assetRequested = true;
            }
        }

        long browserLikeIpCount =
                visitorStates.values()
                        .stream()
                        .filter(state -> state.browserLike)
                        .count();

        long estimatedVisitorCount =
                visitorStates.entrySet()
                        .stream()
                        .filter(entry -> !scannerIps.contains(entry.getKey()))
                        .filter(entry -> isEstimatedVisitor(entry.getValue()))
                        .count();

        List<DailyTrafficDTO> dailyTraffic =
                dailyStates.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> {
                            long estimatedVisitors =
                                    entry.getValue()
                                            .visitors
                                            .entrySet()
                                            .stream()
                                            .filter(visitor ->
                                                    !scannerIps.contains(
                                                            visitor.getKey()
                                                    )
                                            )
                                            .filter(visitor ->
                                                    isEstimatedVisitor(
                                                            visitor.getValue()
                                                    )
                                            )
                                            .count();

                            return new DailyTrafficDTO(
                                    entry.getKey(),
                                    entry.getValue().requests,
                                    estimatedVisitors
                            );
                        })
                        .toList();

        Map<String, Long> popularPageCounts = new HashMap<>();

        for (LogEntry entry : entries) {
            if (isLocalhost(entry.ip())) {
                continue;
            }

            if (!"GET".equalsIgnoreCase(entry.method())) {
                continue;
            }

            if (!isPagePath(entry.path())) {
                continue;
            }

            if (scannerIps.contains(entry.ip())) {
                continue;
            }

            String pagePath = canonicalPagePath(entry.path());

            popularPageCounts.merge(
                    pagePath,
                    1L,
                    Long::sum
            );
        }

        List<PopularPageDTO> popularPages =
                popularPageCounts.entrySet()
                        .stream()
                        .sorted(
                                Comparator
                                        .<Map.Entry<String, Long>>
                                                comparingLong(
                                                        Map.Entry::getValue
                                                )
                                        .reversed()
                                        .thenComparing(Map.Entry::getKey)
                        )
                        .limit(10)
                        .map(entry ->
                                new PopularPageDTO(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .toList();

        return new AdminTrafficResponseDTO(
                OffsetDateTime.now(SEOUL_ZONE),
                periodStart,
                periodEnd,
                totalRequests,
                visitorStates.size(),
                browserLikeIpCount,
                estimatedVisitorCount,
                scannerRequestCount,
                scannerIps.size(),
                dailyTraffic,
                popularPages
        );
    }

    private List<LogEntry> readLogEntries() {
        if (logDirectory == null) {
            return List.of();
        }

        if (!Files.isDirectory(logDirectory)) {
            return List.of();
        }

        List<LogEntry> entries = new ArrayList<>();

        for (String fileName : LOG_FILE_NAMES) {
            Path logFile = logDirectory.resolve(fileName);

            if (!Files.isRegularFile(logFile)) {
                continue;
            }

            readLogFile(logFile, entries);
        }

        return entries;
    }

    private void readLogFile(
            Path logFile,
            List<LogEntry> entries
    ) {
        try (
                BufferedReader reader =
                        Files.newBufferedReader(
                                logFile,
                                StandardCharsets.UTF_8
                        )
        ) {
            String line;

            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLogLine(line);

                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to read Nginx access log: " + logFile,
                    e
            );
        }
    }

    private LogEntry parseLogLine(String line) {
        Matcher matcher = NGINX_LOG_PATTERN.matcher(line);

        if (!matcher.matches()) {
            return null;
        }

        String ip = matcher.group(1);
        String timestampText = matcher.group(2);
        String request = matcher.group(3);
        String userAgent = matcher.group(7);

        String[] requestParts = request.trim().split("\\s+");

        if (requestParts.length < 2) {
            return null;
        }

        String method = requestParts[0];
        String path = normalizePath(requestParts[1]);

        try {
            OffsetDateTime timestamp =
                    OffsetDateTime.parse(
                            timestampText,
                            NGINX_DATE_FORMATTER
                    );

            return new LogEntry(
                    ip,
                    timestamp,
                    method,
                    path,
                    userAgent
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String normalizePath(String requestTarget) {
        if (requestTarget == null || requestTarget.isBlank()) {
            return "/";
        }

        String path = requestTarget;

        int queryIndex = path.indexOf('?');

        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }

        int fragmentIndex = path.indexOf('#');

        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }

        if (path.isBlank()) {
            return "/";
        }

        return path;
    }

    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip);
    }

    private boolean isBrowserLike(String userAgent) {
        String normalized =
                userAgent == null
                        ? ""
                        : userAgent.toLowerCase(Locale.ROOT);

        if (isBotUserAgent(normalized)) {
            return false;
        }

        for (String token : BROWSER_USER_AGENT_PATTERNS) {
            if (normalized.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBotUserAgent(String userAgent) {
        String normalized =
                userAgent == null
                        ? ""
                        : userAgent.toLowerCase(Locale.ROOT);

        for (String token : BOT_USER_AGENT_PATTERNS) {
            if (normalized.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private boolean isScannerPath(String path) {
        String normalized =
                path == null
                        ? ""
                        : path.toLowerCase(Locale.ROOT);

        for (String token : SCANNER_PATH_PATTERNS) {
            if (normalized.contains(token)) {
                return true;
            }
        }

        return false;
    }

    private boolean isPagePath(String path) {
        return path != null
                && PAGE_PATTERN.matcher(path).matches();
    }

    private boolean isAssetPath(String path) {
        return path != null
                && ASSET_PATTERN.matcher(path).matches();
    }

    private boolean isEstimatedVisitor(VisitorState state) {
        return state.browserLike
                && state.pageRequested
                && state.assetRequested;
    }

    private String canonicalPagePath(String path) {
        if ("/".equals(path)) {
            return "/";
        }

        if (path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }

        return path;
    }

    private AdminTrafficResponseDTO emptyResponse() {
        return new AdminTrafficResponseDTO(
                OffsetDateTime.now(SEOUL_ZONE),
                null,
                null,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                List.of(),
                List.of()
        );
    }

    private record LogEntry(
            String ip,
            OffsetDateTime timestamp,
            String method,
            String path,
            String userAgent
    ) {
    }

    private static final class VisitorState {

        private boolean browserLike;
        private boolean pageRequested;
        private boolean assetRequested;
    }

    private static final class DailyState {

        private long requests;

        private final Map<String, VisitorState> visitors =
                new HashMap<>();
    }
}