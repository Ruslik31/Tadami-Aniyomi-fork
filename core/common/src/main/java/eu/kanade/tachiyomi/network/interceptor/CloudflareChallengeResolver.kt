package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import tachiyomi.i18n.MR
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface CloudflareChallengeResolver {
    fun resolve(originalRequest: Request, oldCookie: Cookie?)
}

internal class WebViewCloudflareChallengeResolver(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val mainExecutor: Executor,
    private val createWebView: (Request) -> WebView,
    private val parseHeaders: (Headers) -> Map<String, String>,
    private val isWebViewOutdated: (WebView) -> Boolean,
) : CloudflareChallengeResolver {

    @SuppressLint("SetJavaScriptEnabled")
    override fun resolve(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        // Single timeout source: the waiter's polling clock and the latch backstop share
        // the same constant so they can never drift apart.
        val waiter = CloudflareClearanceWaiter(maxWaitMs = CHALLENGE_RESOLVE_TIMEOUT_MS)

        var webview: WebView? = null
        var hasInteractiveWidget = false
        var isWebViewOutdatedNow = false
        var released = false
        var pollingScheduled = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        fun release() {
            if (released) return
            released = true
            handler.removeCallbacksAndMessages(null)
            latch.countDown()
        }

        fun cookiePresent(): Boolean {
            return hasNewCloudflareClearance(originalRequest, oldCookie)
        }

        // Poll for the cf_clearance cookie. Cloudflare solves the challenge asynchronously
        // (after the first onPageFinished) and may set the cookie without a redirect, so a
        // single onPageFinished check is not enough -- we must keep probing.
        val poller = object : Runnable {
            override fun run() {
                if (released) return
                if (waiter.tick(::cookiePresent)) {
                    CookieManager.getInstance().flush()
                    release()
                    return
                }
                if (waiter.shouldRelease) {
                    release()
                    return
                }
                handler.postDelayed(this, waiter.pollIntervalMs)
            }
        }

        fun schedulePolling() {
            if (released || pollingScheduled) return
            pollingScheduled = true
            handler.post(poller)
        }

        mainExecutor.execute {
            val createdWebView = createWebView(originalRequest)
            webview = createdWebView

            createdWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Success is signaled only by the presence of the cf_clearance cookie, not by
                    // the page finishing loading. The challenge JS runs after this callback, so
                    // releasing here (the old behaviour) killed the WebView mid-solve and the
                    // bypass failed even though the cookie would have appeared moments later.
                    if (waiter.onPageFinished(::cookiePresent)) {
                        CookieManager.getInstance().flush()
                        release()
                        return
                    }
                    // Do NOT release here on Turnstile/widget detection. The probe only proves
                    // that a turnstile element is present in the DOM, which is also true for
                    // challenges Cloudflare auto-solves asynchronously after onPageFinished --
                    // releasing now would kill the WebView mid-solve (the exact bug this poll
                    // exists to fix). Interactivity is therefore only reported after the polling
                    // window has elapsed, via the sync fallback in the post-latch block below.
                    schedulePolling()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        // A main-frame load failure means the challenge cannot complete.
                        release()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: WebResourceResponse,
                ) {
                    if (request.isForMainFrame) {
                        // Don't release on HTTP errors: CF error codes mark a challenge (keep
                        // polling for the cookie) and other status codes (429/502 "page expired"
                        // mid-challenge) are expected, so releasing here would abort a solve.
                    }
                }
            }

            createdWebView.loadUrl(origRequestUrl, headers)
        }

        // Stage 1 -- optimistic wait. Most managed challenges self-solve within a couple of
        // seconds, so we first wait only up to the *soft* limit; the poller keeps running in
        // the background and releases the latch as soon as the cookie appears.
        try {
            latch.await(waiter.softLimitMs, TimeUnit.MILLISECONDS)

            // Stage 2 -- fast-fail interactive challenges. If no cookie arrived within the
            // optimistic window and the page shows a human-verification widget (Turnstile), that
            // challenge will never self-solve, so fail it here instead of sitting out the full
            // timeout. Widget-free challenges (or a cookie that appears during the probe) keep
            // their remaining time below, so slow auto-solves are not aborted.
            if (!waiter.bypassed && !hasInteractiveWidget && detectInteractiveWidgetSync(webview)) {
                hasInteractiveWidget = true
                release()
            } else if (!waiter.bypassed) {
                // Stage 3 -- give a widget-free auto-solve the rest of the window up to the hard cap.
                latch.await(CHALLENGE_RESOLVE_TIMEOUT_MS - waiter.softLimitMs, TimeUnit.MILLISECONDS)
            }
        } finally {
            // P5: stop the poller the moment the wait ends - with a congested main thread the
            // poller used to outlive the resolve (running sync cookie probes on main) long
            // after the WebView was destroyed.
            release()
            // Guaranteed teardown: an interrupted worker thread used to unwind before this
            // block ran, leaking the created WebView (an OOM driver on low-heap devices).
            mainExecutor.execute {
                if (!waiter.bypassed) {
                    isWebViewOutdatedNow = webview?.let(isWebViewOutdated) == true
                }

                webview?.run {
                    stopLoading()
                    destroy()
                }
            }
        }

        if (!waiter.bypassed) {
            if (isWebViewOutdatedNow) {
                context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
            } else if (hasInteractiveWidget) {
                context.toast(MR.strings.information_cloudflare_interactive_challenge, Toast.LENGTH_LONG)
                throw CloudflareInteractiveChallengeException()
            }

            throw CloudflareBypassException()
        }
    }

    private fun hasNewCloudflareClearance(originalRequest: Request, oldCookie: Cookie?): Boolean {
        return hasNewCloudflareClearance(
            originalUrl = originalRequest.url,
            oldCookie = oldCookie,
            cookieLookup = { url -> cookieManager.get(url) },
        )
    }

    private fun detectInteractiveWidgetSync(webview: WebView?): Boolean {
        if (webview == null) return false
        val checkLatch = CountDownLatch(1)
        var detected = false
        mainExecutor.execute {
            try {
                webview.evaluateJavascript(INTERACTIVE_WIDGET_PROBE) { result ->
                    detected = result == "true"
                    checkLatch.countDown()
                }
            } catch (_: Throwable) {
                checkLatch.countDown()
            }
        }
        checkLatch.await(2, TimeUnit.SECONDS)
        return detected
    }
}

// P7: single source of the widget probe - shared with the manual WebView screen.
internal val INTERACTIVE_WIDGET_PROBE = CloudflareChallengeDetector.interactiveWidgetProbeJs

// P4: a clearance counts as new only when it differs from the cookie we entered the solve
// with, and only on the ORIGINAL host. The previous cross-host OR-branch accepted any
// cf_clearance sitting on a different domain (which cannot clear the original host),
// releasing the solve early into a guaranteed failure. CF sets the clearance on the
// registrable domain, so a redirect mid-challenge still lands in the original-host lookup.
internal fun hasNewCloudflareClearance(
    originalUrl: HttpUrl,
    oldCookie: Cookie?,
    cookieLookup: (HttpUrl) -> List<Cookie>,
): Boolean {
    val cookie = cookieLookup(originalUrl).firstOrNull { it.name == "cf_clearance" }
    return cookie != null && cookie != oldCookie
}

private const val CHALLENGE_RESOLVE_TIMEOUT_MS = 30_000L

internal open class CloudflareBypassException : Exception()
internal class CloudflareInteractiveChallengeException : CloudflareBypassException()
