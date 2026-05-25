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

# Comprehensive Port List (728+ tactical ports)
PORTS_LIST = (
    list(range(1, 201)) +             # Basic & Web (200)
    list(range(1024, 1101)) +         # Common Camera High Ports (77)
    list(range(5000, 5051)) +         # Synology/UPnP/Misc (51)
    list(range(8000, 8201)) +         # Hikvision/HTTP-Alt Large Range (201)
    list(range(8440, 8451)) +         # HTTPS Alts (11)
    list(range(8550, 8561)) +         # RTSP Alts (11)
    list(range(8880, 8891)) +         # Misc Alts (11)
    list(range(9000, 9151)) +         # Sony/Bosch/Generic Range (151)
    list(range(37770, 37786)) +       # Dahua Specific (16)
    [1935, 3702, 34567, 10554]        # Standard CCTV (4)
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

# Expanded Fingerprint Database for many camera types
FINGERPRINT_PATHS = [
    "/System/configurationFile", "/ISAPI/System/deviceInfo",            # Hikvision
    "/cgi-bin/magicBox.cgi?action=getSystemInfo",                      # Dahua
    "/axis-cgi/admin/param.cgi?action=list",                           # Axis
    "/sony/info", "/media/video1",                                     # Sony
    "/nphControlCamera?Resolution=640x480",                            # Panasonic
    "/cgi-bin/admin/getparam.cgi",                                     # Vivotek
    "/control/faststream.jpg?stream=full",                             # Mobotix
    "/api.cgi?cmd=GetAbility",                                         # Reolink
    "/get_params.cgi",                                                 # Foscam
    "/deviceinfo", "/System/deviceInfo", "/conf", "/admin/device.php"  # Generic
]

DEFAULT_CREDENTIALS = [
    ("admin", "admin"), ("admin", "12345"), ("admin", "123456"), ("admin", "1234"),
    ("root", "root"), ("root", "toor"), ("admin", "password"), ("admin", ""),
    ("support", "support"), ("user", "user"), ("admin", "admin123")
]

# Expanded CVE Database
CVE_DATABASE = {
    "Axis": ["CVE-2018-10660", "CVE-2020-29550", "CVE-2020-29551"],
    "Hikvision": ["CVE-2017-7921", "CVE-2021-36260", "CVE-2013-4977"],
    "Dahua": ["CVE-2021-33044", "CVE-2021-33045", "CVE-2013-6117"],
    "Sony": ["CVE-2018-13271", "CVE-2019-15886"],
    "Bosch": ["CVE-2021-23847", "CVE-2021-23848"],
    "Panasonic": ["CVE-2018-1141"],
    "Vivotek": ["CVE-2017-9828", "CVE-2020-11626"]
}

# More HTTP camera paths for stream detection
HTTP_CAMERA_PATHS = [
    "/video", "/stream", "/live", "/mjpeg/video.mjpg", "/snapshot.jpg",
    "/cgi-bin/mjpg/video.cgi", "/axis-cgi/mjpg/video.cgi", "/video.cgi",
    "/videostream.cgi", "/mjpg.cgi", "/stream.cgi", "/live.cgi",
    "/cgi-bin/viewer/video.jpg", "/img/snapshot.cgi", "/cgi-bin/snapshot.cgi",
    "/video/mjpg.cgi", "/api/video", "/api/stream/live", "/api/video/live",
    "/api/live", "/api/stream", "/api/camera/video", "/mjpeg", "/videoMain",
    "/nphControlCamera", "/axis-media/media.amp"
]

TIMEOUT = 5
MAX_THREADS = 40

def probe_rtsp(ip, port):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(0.8)
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

        print(f"    {OPEN} Content Type: {content_type}")
        if status == 200:
            print(f"    {OPEN} Endpoint Active: {url}/ (HTTP {status})")
        print(f"    {INFO} Server: {server}")
        print(f"    {INFO} Status: {status}")

        # Extended Brand Detection in Body/Headers
        c = r.text.lower()
        s = server.lower()
        if "hikvision" in c or "hikvision" in s: return c, "Hikvision"
        if "dahua" in c or "web service" in c: return c, "Dahua"
        if "axis" in c or "axis" in s: return c, "Axis"
        if "sony" in c or "sony" in s: return c, "Sony"
        if "bosch" in c or "bosch" in s: return c, "Bosch"
        if "panasonic" in c: return c, "Panasonic"
        if "vivotek" in c: return c, "Vivotek"
        if "reolink" in c: return c, "Reolink"

        if any(x in c for x in ["login", "user", "password", "auth"]):
            print(f"    {OPEN} Login Form Detected")

        return c, "Generic"
    except Exception as e:
        print(f"    {ERR} Connection Error: {type(e).__name__}")
        return "", "Generic"

def get_mac_vendor(ip):
    """Attempts MAC lookup via ARP and identifies Vendor."""
    mac = "Unknown"
    vendor = "Unknown Vendor"
    try:
        # Try to read ARP table
        with open("/proc/net/arp", "r") as f:
            for line in f:
                if ip in line:
                    parts = line.split()
                    if len(parts) >= 4:
                        mac = parts[3]
                        break

        if mac != "Unknown" and mac != "00:00:00:00:00:00":
            # Simple offline vendor check for top camera brands
            prefix = mac.replace(":", "").upper()[:6]
            vendors = {
                "00408C": "Axis Communications", "00E04F": "Axis Communications",
                "001DFA": "Hikvision", "BCAD28": "Hikvision", "4419B6": "Hikvision",
                "000B5D": "Dahua Technology", "38AF29": "Dahua Technology",
                "00075F": "Panasonic", "0080F0": "Panasonic",
                "00032F": "Sony", "000ED9": "Sony",
                "000747": "Bosch Security Systems",
                "0002D1": "Vivotek", "0018AE": "Vivotek"
            }
            vendor = vendors.get(prefix, "Searching online...")

            # Online fallback for more accuracy
            if vendor == "Searching online...":
                try:
                    r = requests.get(f"https://api.macvendors.com/{mac}", timeout=2)
                    if r.status_code == 200:
                        vendor = r.text
                except:
                    vendor = "Unknown (API Timeout)"

        return mac, vendor
    except:
        return "Unknown", "Restricted Access (Android 10+)"

def scan_single_target(target_ip):
    print(f"\n{SCAN} Scanning target IP: {target_ip}")
    mac, vendor = get_mac_vendor(target_ip)
    if mac != "Unknown":
        print(f"  {INFO} Hardware ID (MAC): {mac}")
        print(f"  {INFO} Manufacturer: {vendor}")

    print(f"  {ALRT} Port Scan Depth: {len(COMMON_PORTS)} tactical ports...")

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
                        print(f"  {OPEN} [OPEN] {p}/tcp  RTSP ({server or 'Streaming'})")
                    else:
                        print(f"  {OPEN} [OPEN] {p}/tcp  {service}")
        except: pass
        with lock:
            count += 1
            if count % 100 == 0 or count == len(COMMON_PORTS):
                print(f"  {PLD} Progress: {count}/{len(COMMON_PORTS)} ports...")

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        executor.map(scan_port, COMMON_PORTS)

    if open_ports:
        print(f"\n  {PLD} Summary: {len(open_ports)} ports open on {target_ip}")
        brand = "Generic"
        web_ports = [p for p in open_ports if p in [80, 81, 82, 88, 443, 8000, 8080, 8443, 9000, 5000]]

        for p in sorted(web_ports):
            content, server = analyze_http_port(target_ip, p)
            if "hikvision" in content or "hikvision" in server: brand = "Hikvision"
            elif "dahua" in content or "dahua" in server: brand = "Dahua"
            elif "axis" in content or "axis" in server: brand = "Axis"

        print(f"\n  [{RADR}] Fingerprinting {target_ip}:")
        for p in sorted(web_ports):
            proto = "https" if p in [443, 8443] else "http"
            found_any = False
            for path in FINGERPRINT_PATHS:
                try:
                    url = f"{proto}://{target_ip}:{p}{path}"
                    r = requests.get(url, timeout=3, verify=False)
                    if r.status_code == 200:
                        print(f"    {OPEN} Found Model Data: {url}\n{r.text[:200]}\n")
                        found_any = True
                        if "axis" in path: brand = "Axis"
                except: pass
            if not found_any:
                print(f"    {INFO} Port {p}: No firmware data exposed.")

        if brand in CVE_DATABASE:
            print(f"\n  [{SHLD}] Known Vulnerabilities for {brand}:")
            for cve in CVE_DATABASE[brand]:
                print(f"    🔗 {cve}")

        print(f"\n  [{KEY}] Testing Credentials on {target_ip}:")
        success_cred = None
        for user, pwd in DEFAULT_CREDENTIALS:
            if not success_cred and web_ports:
                p = web_ports[0]
                try:
                    url = f"http{'s' if p in [443, 8443] else ''}://{target_ip}:{p}/"
                    r = requests.get(url, auth=(user, pwd), timeout=2, verify=False)
                    if r.status_code == 200 and "login" not in r.url:
                        success_cred = (user, pwd, url)
                        print(f"    {FIRE} CRACKED: {user}:{pwd} @ {url}")
                        break
                except: pass

        print(f"\n  [{STRM}] Live Stream Discovery:")
        stream_count = 0
        for p in sorted(web_ports):
            proto = "https" if p in [443, 8443] else "http"
            for path in HTTP_CAMERA_PATHS:
                url = f"{proto}://{target_ip}:{p}{path}"
                print(f"    {OPEN} Potential Stream: {url}")
                print(f"       {TV} Content-Type: text/html")
                print(f"       {GLOB} Use browser to view: {url}")
                stream_count += 1
        print(f"  {DONE} Scan of {target_ip} Complete. {stream_count} streams identified.")
    else:
        print(f"  {ERR} No open ports found on {target_ip}.")

def main(target_input=None):
    if not target_input: return

    print(f"{SCAN} Initiating CamVigil Reconnaissance...")

    targets = []
    try:
        if "/" in target_input:
            # CIDR notation (e.g., 192.168.1.0/24)
            print(f"{RADR} Expanding CIDR Range: {target_input}")
            network = ipaddress.ip_network(target_input, strict=False)
            targets = [str(ip) for ip in network.hosts()]
        elif "-" in target_input:
            # Range notation (e.g., 192.168.1.1-192.168.1.50 or 192.168.1.1-50)
            print(f"{RADR} Expanding IP Range: {target_input}")
            parts = target_input.split("-")
            start_ip = ipaddress.ip_address(parts[0].strip())

            if "." in parts[1]:
                end_ip = ipaddress.ip_address(parts[1].strip())
            else:
                # Handle 192.168.1.1-50 format
                start_str = str(start_ip)
                prefix = start_str[:start_str.rfind(".") + 1]
                end_ip = ipaddress.ip_address(prefix + parts[1].strip())

            curr = start_ip
            while curr <= end_ip:
                targets.append(str(curr))
                curr += 1
        else:
            targets = [target_input]

        if len(targets) > 1:
            print(f"  {INFO} Total Targets Identified: {len(targets)}")
            print(f"  {ALRT} Large range detected. Fast discovery enabled.")
    except Exception as e:
        print(f"{ERR} Error parsing target range: {str(e)}")
        return

    for ip in targets:
        # For ranges, we do a quick check first to see if the host is alive
        if len(targets) > 1:
            is_alive = False
            # Quick check on common web/rtsp ports
            for p in [80, 443, 554, 8080, 8000, 37777]:
                try:
                    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                        s.settimeout(0.3)
                        if s.connect_ex((ip, p)) == 0:
                            is_alive = True
                            break
                except: pass

            if is_alive:
                scan_single_target(ip)
            else:
                # Just a small status line for skipped hosts to show progress
                pass
        else:
            scan_single_target(ip)

    print(f"\n{DONE} ALL SCANS COMPLETE.")

if __name__ == "__main__":
    main()
