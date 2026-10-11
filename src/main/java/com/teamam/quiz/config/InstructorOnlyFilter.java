package com.teamam.quiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * The app runs on the instructor's own laptop, so "instructor" means "request from this machine".
 * Every /api call is instructor-only (default deny) except the three a student's phone needs;
 * the instructor page itself and the H2 console are local-only too.
 */
@Component
public class InstructorOnlyFilter extends OncePerRequestFilter {

    public static final String MESSAGE = "This action is only available on the instructor's laptop.";

    private static final List<Pattern> STUDENT_API = List.of(
            Pattern.compile("POST /api/roster/\\d+/join"),
            Pattern.compile("POST /api/sessions/\\d+/join-ticket"),
            Pattern.compile("GET /api/sessions/\\d+/state"),
            Pattern.compile("POST /api/sessions/\\d+/doubts"),
            Pattern.compile("POST /api/sessions/\\d+/integrity"));

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /** Runs before every request: blocks instructor-only actions unless they come from this laptop. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (requiresInstructor(request) && !isLoopback(request.getRemoteAddr())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + MESSAGE + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Is this an instructor-only address? All /api calls are, except the 5 that phones need. */
    static boolean requiresInstructor(HttpServletRequest request) {
        String path = StringUtils.cleanPath(PATHS.getPathWithinApplication(request));
        if (path.startsWith("/api/")) {
            String call = request.getMethod() + " " + path;
            return STUDENT_API.stream().noneMatch(allowed -> allowed.matcher(call).matches());
        }
        return path.equals("/instructor.html")
                || path.startsWith("/js/instructor")
                || path.startsWith("/h2-console");
    }

    /** Did the request come from this laptop itself (localhost)? */
    private static boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
