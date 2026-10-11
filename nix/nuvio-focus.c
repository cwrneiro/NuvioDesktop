/*
 * nuvio-focus — re-send WM_TAKE_FOCUS to the Nuvio toplevel so Java/AWT
 * self-focuses its X focus proxy (ICCCM Globally Active input model).
 *
 * Root cause (verified on the target, 2026-10-05): Java AWT advertises
 * WM_HINTS input=False + WM_TAKE_FOCUS. wlroots' XWM sends WM_TAKE_FOCUS
 * exactly once at first activation and never calls XSetInputFocus for
 * Globally Active windows; when that one message is missed during the
 * window's first map (observed with Nuvio 0.1.27-alpha on sway 1.12),
 * X focus stays on the toplevel window, which selects no KeyPress mask —
 * every key is discarded and the app looks dead to the gamepad.
 * Re-sending WM_TAKE_FOCUS makes AWT request focus for its invisible
 * FocusProxy child; after that the full keyboard path works.
 *
 * Exit codes: 0 = X focus reached a child of the app toplevel (the AWT
 * focus proxy); 1 = window never appeared (~20 s); 2 = focus never reached
 * the app after the retries (~10 s). All are non-fatal for the launcher
 * (it only needs to succeed when the app is alive).
 */
#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

static Window find_by_class(Display *d, Window w, const char *cls) {
    Window root, parent, *kids = NULL;
    unsigned int n = 0;
    XClassHint ch;
    if (XGetClassHint(d, w, &ch)) {
        if (ch.res_class && strstr(ch.res_class, cls)) {
            XFree(ch.res_class);
            XFree(ch.res_name);
            return w;
        }
        if (ch.res_class) XFree(ch.res_class);
        if (ch.res_name) XFree(ch.res_name);
    }
    if (!XQueryTree(d, w, &root, &parent, &kids, &n)) return 0;
    Window res = 0;
    for (unsigned int i = 0; i < n && !res; i++)
        res = find_by_class(d, kids[i], cls);
    if (kids) XFree(kids);
    return res;
}

static void send_take_focus(Display *d, Window t) {
    Atom protocols = XInternAtom(d, "WM_PROTOCOLS", False);
    Atom take_focus = XInternAtom(d, "WM_TAKE_FOCUS", False);
    XEvent ev;
    memset(&ev, 0, sizeof ev);
    ev.xclient.type = ClientMessage;
    ev.xclient.window = t;
    ev.xclient.message_type = protocols;
    ev.xclient.format = 32;
    ev.xclient.data.l[0] = take_focus;
    ev.xclient.data.l[1] = CurrentTime;
    XSendEvent(d, t, False, NoEventMask, &ev);
    XFlush(d);
}

/* True when w is a descendant of ancestor. AWT's focus proxy is a child of
 * the toplevel; success must be a real child, NOT just "any window": during
 * startup Xwayland may briefly report PointerRoot (0x1) and the naive
 * "focus != toplevel" check exited on it while the app still could not
 * receive keys (observed on hardware 2026-10-05). */
static int is_descendant(Display *d, Window w, Window ancestor) {
    while (w != 0 && w != PointerRoot && w != ancestor) {
        Window root, parent, *kids = NULL;
        unsigned int n = 0;
        if (!XQueryTree(d, w, &root, &parent, &kids, &n)) return 0;
        if (kids) XFree(kids);
        if (parent == 0 || parent == root) return 0;
        w = parent;
    }
    return w == ancestor;
}

/* Xlib's default error handler terminates the process. Focus changes race
 * with window creation/destruction during startup, so a BadWindow from
 * XQueryTree is expected and must be ignored (the query then returns 0). */
static int ignore_x_error(Display *d, XErrorEvent *e) {
    (void)d;
    (void)e;
    return 0;
}

int main(int argc, char **argv) {
    /* WM_CLASS of the Nuvio toplevel (override for other apps if ever needed) */
    const char *cls = argc > 1 ? argv[1] : "com-nuvio-app-MainKt";
    Display *d = XOpenDisplay(NULL);
    if (!d) { fprintf(stderr, "nuvio-focus: no display\n"); return 1; }
    XSetErrorHandler(ignore_x_error);

    Window target = 0;
    /* Wait up to ~20 s for the window to map (JVM/Compose startup) */
    for (int i = 0; i < 80 && !target; i++) {
        target = find_by_class(d, DefaultRootWindow(d), cls);
        if (!target) usleep(250000);
    }
    if (!target) { fprintf(stderr, "nuvio-focus: window not found\n"); XCloseDisplay(d); return 1; }

    Window focus;
    int revert;
    /* Retry up to ~10 s: AWT must create its focus proxy and self-focus it.
     * Each resend is another chance for AWT's WM_TAKE_FOCUS handler to run
     * once its peer is ready; success requires focus on a CHILD of target. */
    for (int i = 0; i < 40; i++) {
        send_take_focus(d, target);
        usleep(250000);
        XGetInputFocus(d, &focus, &revert);
        if (focus != target && is_descendant(d, focus, target)) {
            printf("nuvio-focus: X focus moved 0x%lx -> 0x%lx\n", target, focus);
            XCloseDisplay(d);
            return 0;
        }
    }
    XGetInputFocus(d, &focus, &revert);
    fprintf(stderr, "nuvio-focus: focus did not reach the app (focus=0x%lx, toplevel=0x%lx)\n", focus, target);
    XCloseDisplay(d);
    return 2;
}
