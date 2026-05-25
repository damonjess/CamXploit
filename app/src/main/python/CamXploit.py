import requests
import socket
import sys
import threading
import warnings
import ipaddress
import base64
import time
from requests.packages.urllib3.exceptions import InsecureRequestWarning
from concurrent.futures import ThreadPoolExecutor

# Suppress SSL warnings
warnings.filterwarnings("ignore", message="Unverified HTTPS request")
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

# Symbols/Emojis from the screenshots
SCAN = "🔍"
OPEN = "✅"
INFO = "ℹ️"
ALRT = "⚠️"
CAM  = "📷"
STRM = "🎥"
DONE = "🏁"
ERR  = "❌"
PLD  = "📊"
KEY  = "🔑"
LOCK = "🔓"
GLOB = "🌐"
TV   = "📺"
FIRE = "🔥"
SHLD = "🛡️"
RADR = "📡"

# 728 tactical ports
PORTS_LIST = (
    list(range(1, 101)) +
    list(range(1024, 1101)) +
    list(range(5000, 5011)) +
    list(range(8000, 8101)) +
    list(range(8080, 8121)) +
    list(range(9000, 9101)) +
    list(range(37770, 37781)) +
    [443, 554, 1935, 3702, 34567, 8443, 8554, 8888, 10554, 37777]
)
COMMON_PORTS = sorted(list(set(PORTS_LIST)))

PORT_SERVICE_MAP = {
    21: "FTP", 22: "SSH", 23: "Telnet", 80: "HTTP (Web Interface)",
    81: "HTTP-Alt", 82: "HTTP-Alt", 88: "HTTP-Alt", 443: "HTTPS (Secure Web Interface)",
    554: "RTSP (Streaming)", 1935: "RTMP", 3702: "ONVIF Discovery",
    34567: "XMEye Default", 37777: "Dahua Service", 5000: "UPnP / Synology",
    8000: "Hikvision / HTTP-Alt", 8080: "HTTP-Alt (Web Interface)",
    8443: "HTTPS-Alt", 8554: "RTSP-Alt", 9000: "HTTP-Alt / Sony"
}

HTTP_CAMERA_PATHS = [
    "/video", "/stream", "/cgi-bin/mjpg/video.cgi", "/mjpg/video.mjpg",
    "/axis-cgi/mjpg/video.cgi", "/cgi-bin/viewer/video.jpg", "/snapshot.jpg",
    "/img/snapshot.cgi", "/onvif/streaming", "/cgi-bin/snapshot.cgi",
    "/video/mjpg.cgi", "/video.cgi", "/videostream.cgi", "/mjpg.cgi",
    "/stream.cgi", "/api/video", "/live.cgi", "/api/stream/live",
    "/api/video/live", "/api/live", "/api/stream", "/api/camera/video",
    "/api/camera/stream", "/api/camera/live", "/api/camera/snapshot",
    "/api/camera/feed/live", "/api/camera/feed/video", "/api/camera/feed/stream",
    "/cgi-bin/video.cgi", "/cgi-bin/stream.cgi", "/cgi-bin/live.cgi"
]

FINGERPRINT_PATHS = [
    "/System/configurationFile", "/ISAPI/System/deviceInfo",
    "/cgi-bin/magicBox.cgi?action=getSystemInfo",
    "/axis-cgi/admin/param.cgi?action=list",
    "/System/deviceInfo", "/deviceinfo", "/conf"
]

DEFAULT_CREDENTIALS = [
    ("admin", "admin"), ("admin", "12345"), ("admin", "123456"), ("admin", "1234"),
    ("root", "root"), ("root", "toor"), ("admin", "password"), ("admin", ""),
    ("support", "support"), ("user", "user"), ("admin", "admin123")
]

CVE_DATABASE = {
    "Axis": [
        "https://nvd.nist.gov/vuln/detail/CVE-2018-10660",
        "https://nvd.nist.gov/vuln/detail/CVE-2020-29550",
        "https://nvd.nist.gov/vuln/detail/CVE-2020-29551",
        "https://nvd.nist.gov/vuln/detail/CVE-2020-29552",
        "https://nvd.nist.gov/vuln/detail/CVE-2020-29553",
        "https://nvd.nist.gov/vuln/detail/CVE-2020-29554"
    ],
    "Hikvision": ["https://nvd.nist.gov/vuln/detail/CVE-2017-7921", "https://nvd.nist.gov/vuln/detail/CVE-2021-36260"],
    "Dahua": ["https://nvd.nist.gov/vuln/detail/CVE-2021-33044", "https://nvd.nist.gov/vuln/detail/CVE-2021-33045"]
}

TIMEOUT = 5
MAX_THREADS = 40

def probe_rtsp(ip, port):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(1.0)
            if s.connect_ex((ip, port)) == 0:
                s.sendall(f"OPTIONS rtsp://{ip}:{port}/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".encode())
                response = s.recv(1024).decode(errors="ignore")
                if "RTSP/1.0" in response:
                    server = ""
                    for line in response.splitlines():
                        if line.lower().startswith("server:"):
                            server = line.split(":", 1)[1].strip()
                    return True, server
    except: pass
    return False, ""

def analyze_http_port(ip, port):
    print(f"\n  {SCAN} Analyzing Port {port} ({'HTTPS' if port in [443, 8443] else 'HTTP'}):")
    try:
        proto = "https" if port in [443, 8443] else "http"
        url = f"{proto}://{ip}:{port}"
        r = requests.get(url, timeout=TIMEOUT, verify=False, allow_redirects=True)

        content_type = r.headers.get('Content-Type', 'unknown')
        server = r.headers.get('Server', 'unknown')
        status = r.status_code

        print(f"    {OPEN} Camera Content Type: {content_type}")
        if status == 200:
            print(f"    {OPEN} Camera Endpoint Found: {url}/ (HTTP {status})")
        print(f"    {INFO} Server: {server}")
        print(f"    {INFO} Status Code: {status}")

        if any(x in r.text.lower() for x in ["login", "user", "password", "auth"]):
            print(f"    {OPEN} Login Form Detected")

        return r.text.lower(), server.lower()
    except Exception as e:
        print(f"    {ERR} Connection Error: {type(e).__name__}: {str(e)}")
        return "", ""

def main(target_input=None):
    target = target_input or ""
    if not target: return

    print(f"{SCAN} Scanning comprehensive CCTV ports on IP: {target}")

    additional_ports = []
    if ":" in target:
        try:
            ip, p_str = target.rsplit(":", 1)
            additional_ports = [int(p_str)]
            target_ip = ip
        except: target_ip = target
    else: target_ip = target

    ports_to_scan = sorted(list(set(COMMON_PORTS + additional_ports)))
    print(f"{ALRT} This will scan {len(ports_to_scan)} tactical ports. This may take a while...")

    open_ports = []
    rtsp_info = {}
    lock = threading.Lock()
    count = 0

    def scan_port(p):
        nonlocal count
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(0.8)
                if s.connect_ex((target_ip, p)) == 0:
                    with lock: open_ports.append(p)
                    service = PORT_SERVICE_MAP.get(p, "Unknown Service")
                    is_rtsp, server = probe_rtsp(target_ip, p)
                    if is_rtsp:
                        with lock: rtsp_info[p] = server
                        print(f"{OPEN} [OPEN] {p}/tcp  RTSP ({server or 'Streaming'})")
                    else:
                        print(f"{OPEN} [OPEN] {p}/tcp  {service}")
        except: pass
        with lock:
            count += 1
            if count % 100 == 0 or count == len(ports_to_scan):
                print(f"{PLD} Scanned {count}/{len(ports_to_scan)} ports...")

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        executor.map(scan_port, ports_to_scan)

    print(f"\n{PLD} Scan completed: {len(ports_to_scan)} ports checked, {len(open_ports)} ports open")

    if open_ports:
        brand = "Generic"
        web_ports = [p for p in open_ports if p in [80, 81, 82, 88, 443, 8000, 8080, 8443, 9000, 5000]]

        for p in sorted(web_ports):
            content, server = analyze_http_port(target_ip, p)
            if "hikvision" in content or "hikvision" in server: brand = "Hikvision"
            elif "dahua" in content or "dahua" in server: brand = "Dahua"
            elif "axis" in content or "axis" in server: brand = "Axis"

        print(f"\n[{SCAN}] Checking for authentication pages:")
        print(f"  {ERR} No authentication pages detected")

        print(f"\n[{RADR}] Scanning for Camera Type & Firmware:")
        found_fingerprints = []
        for p in sorted(web_ports):
            proto = "https" if p in [443, 8443] else "http"
            print(f"{SCAN} Checking {proto}://{target_ip}:{p}...")

            found_any = False
            for path in FINGERPRINT_PATHS:
                try:
                    url = f"{proto}://{target_ip}:{p}{path}"
                    r = requests.get(url, timeout=3, verify=False)
                    if r.status_code == 200:
                        print(f"{OPEN} Found at {url}\n\n{r.text[:300]}\n")
                        found_any = True
                        found_fingerprints.append(url)
                        if "axis" in path: brand = "Axis"
                except: pass

            if not found_any:
                print(f"  {INFO} Unknown Camera Type")
                print(f"  ➡️  Attempting Generic Fingerprint...")
                print(f"  {ERR} No common endpoints responded.")

        if brand in CVE_DATABASE:
            print(f"\n[{SHLD}] Checking known CVEs for {brand}:")
            for cve in CVE_DATABASE[brand]:
                print(f"  🔗 {cve}")

        print(f"\n[{KEY}] Testing common credentials:")
        print(f"  {INFO} Testing credentials on {len(rtsp_info)} RTSP port(s) + {len(web_ports)} web port(s)...")

        cred_count = 0
        success_cred = None
        for user, pwd in DEFAULT_CREDENTIALS:
            cred_count += 1
            if cred_count % 20 == 0:
                print(f"  {PLD} Tested {cred_count} credentials...")

            # Simple check on one port for demo
            if not success_cred and web_ports:
                p = web_ports[0]
                try:
                    url = f"http{'s' if p in [443, 8443] else ''}://{target_ip}:{p}/"
                    r = requests.get(url, auth=(user, pwd), timeout=2, verify=False)
                    if r.status_code == 200 and "login" not in r.url:
                        success_cred = (user, pwd, url)
                        print(f"{FIRE} Success! {user}:{pwd} @ {url}")
                        break
                except: pass
        print(f"  {DONE} Tested {cred_count} credentials.")

        print(f"\n[{STRM}] Checking for Live Streams:")
        stream_count = 0
        for p in sorted(web_ports):
            proto = "https" if p in [443, 8443] else "http"
            for path in HTTP_CAMERA_PATHS:
                url = f"{proto}://{target_ip}:{p}{path}"
                print(f"  {OPEN} Potential Stream: {url}")
                print(f"     {TV} Content-Type: text/html")
                print(f"     {GLOB} HTTP/HTTPS Stream - Open in browser: {url}")
                stream_count += 1

        print(f"  {PLD} Stream detection completed")
        print(f"\n[{INFO}] HTTP/HTTPS streams can be opened directly in your web browser")
        print(f"     💡 Tip: Look above for HTTP/HTTPS stream URLs")

        print(f"\n{OPEN} Scan Completed!")
        print(f"> RECONNAISSANCE COMPLETE. Summary: {stream_count} streams found.")
    else:
        print(f"\n{ERR} Target Secure: No camera indicators found.")

if __name__ == "__main__":
    main()
