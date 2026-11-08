/* eadrelay: 把 TCP 127.0.0.1:9333 转发进 WebView 的抽象 unix socket。
 * 编译（Termux）：
 *   pkg install clang -y
 *   clang -O2 -static -o eadrelay eadrelay.c
 * 部署：
 *   mkdir -p /data/local/tmp/ead_ws/bin
 *   cp eadrelay /data/local/tmp/ead_ws/bin/ && chmod 755 /data/local/tmp/ead_ws/bin/eadrelay
 * 手动运行：eadrelay webview_devtools_remote_<pid>
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <poll.h>

#define PORT 9333

static void pump(int a, int b) {
    char buf[8192];
    struct pollfd p[2];
    p[0].fd = a; p[0].events = POLLIN; p[0].revents = 0;
    p[1].fd = b; p[1].events = POLLIN; p[1].revents = 0;
    for (;;) {
        if (poll(p, 2, -1) < 0) {
            if (errno == EINTR) continue;
            return;
        }
        for (int i = 0; i < 2; i++) {
            if (!(p[i].revents & (POLLIN | POLLHUP | POLLERR))) continue;
            int from = i == 0 ? a : b;
            int to = i == 0 ? b : a;
            ssize_t n = read(from, buf, sizeof buf);
            if (n <= 0) return;
            ssize_t off = 0;
            while (off < n) {
                ssize_t w = write(to, buf + off, (size_t) (n - off));
                if (w <= 0) return;
                off += w;
            }
        }
    }
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: eadrelay <abstract-socket-name>\n");
        return 2;
    }
    const char *name = argv[1];
    int ls = socket(AF_INET, SOCK_STREAM, 0);
    if (ls < 0) { perror("socket"); return 1; }
    int on = 1;
    setsockopt(ls, SOL_SOCKET, SO_REUSEADDR, &on, sizeof on);
    struct sockaddr_in sa;
    memset(&sa, 0, sizeof sa);
    sa.sin_family = AF_INET;
    sa.sin_port = htons(PORT);
    sa.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(ls, (struct sockaddr *) &sa, sizeof sa) < 0) { perror("bind 9333"); return 1; }
    if (listen(ls, 8) < 0) { perror("listen"); return 1; }
    fprintf(stderr, "eadrelay listening 127.0.0.1:%d -> @%s\n", PORT, name);
    for (;;) {
        int c = accept(ls, NULL, NULL);
        if (c < 0) {
            if (errno == EINTR) continue;
            perror("accept");
            return 1;
        }
        int u = socket(AF_UNIX, SOCK_STREAM, 0);
        if (u < 0) { close(c); continue; }
        struct sockaddr_un un;
        memset(&un, 0, sizeof un);
        un.sun_family = AF_UNIX;
        un.sun_path[0] = 0;                     /* 抽象命名空间 */
        strncpy(un.sun_path + 1, name, sizeof(un.sun_path) - 2);
        socklen_t len = (socklen_t) (offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name));
        if (connect(u, (struct sockaddr *) &un, len) < 0) {
            close(c);
            close(u);
            continue;
        }
        pid_t pid = fork();
        if (pid == 0) {
            close(ls);
            pump(c, u);
            _exit(0);
        }
        close(c);
        close(u);
    }
}
