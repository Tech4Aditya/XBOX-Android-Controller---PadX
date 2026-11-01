// PadX PC server - exposes a virtual Xbox 360 controller (ViGEmBus) fed by UDP packets from the phone.
// Protocol (little-endian, 15 bytes): magic 0xCB | seq u16 | buttons u16 | LT u8 | RT u8 | LX,LY,RX,RY s16
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <ViGEm/Client.h>
#include <cstdio>
#include <cstdint>
#include <cstring>

#pragma comment(lib, "ws2_32.lib")
#pragma comment(lib, "setupapi.lib")

#pragma pack(push, 1)
struct Pkt { uint8_t magic; uint16_t seq; uint16_t buttons; uint8_t lt, rt; int16_t lx, ly, rx, ry; };
#pragma pack(pop)

static const int PORT = 26760;

static void printLocalIPs() {
    char host[256];
    if (gethostname(host, sizeof host) != 0) return;
    addrinfo hints{}, *res = nullptr;
    hints.ai_family = AF_INET;
    if (getaddrinfo(host, nullptr, &hints, &res) != 0) return;
    puts("PC addresses (use one manually if auto-discovery fails):");
    for (auto* p = res; p; p = p->ai_next) {
        char ip[64];
        inet_ntop(AF_INET, &((sockaddr_in*)p->ai_addr)->sin_addr, ip, sizeof ip);
        printf("  %s\n", ip);
    }
    freeaddrinfo(res);
}

int main() {
    WSADATA w;
    WSAStartup(MAKEWORD(2, 2), &w);

    PVIGEM_CLIENT client = vigem_alloc();
    if (!client || !VIGEM_SUCCESS(vigem_connect(client))) {
        puts("ViGEmBus driver not found. Install it first, then rerun.");
        return 1;
    }
    PVIGEM_TARGET pad = vigem_target_x360_alloc();
    if (!VIGEM_SUCCESS(vigem_target_add(client, pad))) {
        puts("Failed to create virtual Xbox pad.");
        return 1;
    }

    SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    sockaddr_in a{};
    a.sin_family = AF_INET;
    a.sin_port = htons(PORT);
    a.sin_addr.s_addr = INADDR_ANY;
    if (bind(s, (sockaddr*)&a, sizeof a) == SOCKET_ERROR) {
        printf("Bind failed on UDP %d (port in use?)\n", PORT);
        return 1;
    }
    DWORD timeoutMs = 250;
    setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, (const char*)&timeoutMs, sizeof timeoutMs);

    printf("PadX server up on UDP %d. Open the app on your phone.\n", PORT);
    printLocalIPs();

    uint16_t lastSeq = 0;
    bool active = false;
    DWORD lastRx = GetTickCount();
    char buf[64];

    for (;;) {
        sockaddr_in from{};
        int fl = sizeof from;
        int n = recvfrom(s, buf, sizeof buf, 0, (sockaddr*)&from, &fl);

        if (n > 0) {
            if (n >= 17 && memcmp(buf, "CYBERPAD_DISCOVER", 17) == 0) {
                const char* reply = "CYBERPAD_HERE";
                sendto(s, reply, (int)strlen(reply), 0, (sockaddr*)&from, fl);
                continue;
            }
            if (n == (int)sizeof(Pkt) && (uint8_t)buf[0] == 0xCB) {
                Pkt p;
                memcpy(&p, buf, sizeof p);
                if (active && (int16_t)(p.seq - lastSeq) <= 0) continue;  // stale/duplicate
                active = true;
                lastSeq = p.seq;
                lastRx = GetTickCount();

                XUSB_REPORT r;
                XUSB_REPORT_INIT(&r);
                r.wButtons = p.buttons;
                r.bLeftTrigger = p.lt;
                r.bRightTrigger = p.rt;
                r.sThumbLX = p.lx;
                r.sThumbLY = p.ly;
                r.sThumbRX = p.rx;
                r.sThumbRY = p.ry;
                vigem_target_x360_update(client, pad, r);
            }
        } else if (active && GetTickCount() - lastRx > 1500) {
            // Phone vanished: release everything so the character doesn't run off a cliff.
            XUSB_REPORT r;
            XUSB_REPORT_INIT(&r);
            vigem_target_x360_update(client, pad, r);
            active = false;
            puts("Phone disconnected - pad released.");
        }
    }
}
