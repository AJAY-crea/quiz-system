package com.teamam.quiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
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
 * "Instructor" means: a request from the laptop running the app, or from a browser that opened the
 * instructor's secret link (it leaves a cookie). Every /api call is instructor-only (default deny)
 * except the few a student's phone needs; the instructor page is instructor-only too. The H2 database
 * console is laptop-only, even with the link: it can read and change everything.
 */
@Component
public class InstructorOnlyFilter extends OncePerRequestFilter {

    public static final String MESSAGE = "This action is only available to the instructor.";
    public static final String COOKIE = "quiz_instructor";

    /** 12 hours: one teaching day; open the link again the next day. */
    private static final int COOKIE_SECONDS = 12 * 60 * 60;

    private final InstructorKey instructorKey;

    public InstructorOnlyFilter(InstructorKey instructorKey) {
        this.instructorKey = instructorKey;
    }

    private static final List<Pattern> STUDENT_API = List.of(
            Pattern.compile("POST /api/roster/\\d+/join"),
            Pattern.compile("POST /api/sessions/\\d+/join-ticket"),
            Pattern.compile("GET /api/sessions/\\d+/state"),
            Pattern.compile("GET /api/sessions/\\d+/my-marks"),
            Pattern.compile("POST /api/sessions/\\d+/doubts"),
            Pattern.compile("GET /api/sessions/\\d+/doubts/queue"),
            Pattern.compile("POST /api/sessions/\\d+/doubts/\\d+/upvote"),
            Pattern.compile("POST /api/sessions/\\d+/integrity"));

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /** Runs before every request: blocks instructor-only actions unless they come from the instructor. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = StringUtils.cleanPath(PATHS.getPathWithinApplication(request));
        if (path.equals("/instructor.html") && request.getParameter("key") != null) {
            openInstructorLink(request, response);
            return;
        }
        boolean allowed = path.startsWith("/h2-console")
                ? isFromThisLaptop(request)
                : !requiresInstructor(request) || isFromThisLaptop(request) || hasInstructorCookie(request);
        if (!allowed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + MESSAGE + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * The instructor opened her secret link: remember it in a cookie and send her to the plain address,
     * so the key doesn't stay in the address bar (or on the projector, or in the browser's history).
     */
    private void openInstructorLink(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!instructorKey.matches(request.getParameter("key"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("This instructor link is wrong or out of date. Ask for the current one.");
            return;
        }
        boolean https = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        // SameSite=Strict: another website can't make her browser send this cookie (no cross-site requests).
        response.addHeader("Set-Cookie", COOKIE + "=" + instructorKey.value() + "; Path=/; Max-Age=" + COOKIE_SECONDS
                + "; HttpOnly; SameSite=Strict" + (https ? "; Secure" : ""));
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect("/instructor.html");
    }

    /** Did this browser open the instructor's link? */
    private boolean hasInstructorCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName()) && instructorKey.matches(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Is this an instructor-only address? All /api calls are, except the ones phones need (STUDENT_API). */
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

    // Headers a proxy or tunnel (Cloudflare Tunnel, ngrok, a reverse proxy) adds to say "this came from elsewhere".
    private static final List<String> PROXY_HEADERS =
            List.of("Forwarded", "X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP");

    /**
     * Is this really the instructor at the laptop? It must come from localhost AND not have passed
     * through a tunnel: a tunnel program runs on the laptop too, so its traffic also looks local.
     */
    static boolean isFromThisLaptop(HttpServletRequest request) {
        return isLoopback(request.getRemoteAddr())
                && PROXY_HEADERS.stream().noneMatch(header -> request.getHeader(header) != null);
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
