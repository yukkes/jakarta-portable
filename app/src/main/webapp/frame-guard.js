// Clickjacking guard for the static pages, loaded first in <head>.
//
// The servers send `Content-Security-Policy: frame-ancestors 'none'` with the static files on
// Quarkus, Liberty and WildFly, but Payara and GlassFish have no setting for static-file headers,
// and a CSP given in a <meta> tag ignores frame-ancestors. So the page refuses to render inside
// someone else's frame on every runtime. (CSSOM writes are allowed under style-src 'self'.)
if (window.top !== window.self) {
    document.documentElement.style.display = 'none';
    try {
        window.top.location = window.self.location.href;
    } catch (ignored) {
        // A sandboxed frame may not navigate the top window; the page stays hidden.
    }
}
