package com.spyboy.camxploit

/**
 * Best-effort LAN device identification.
 *
 * Modern Android builds do not let apps read /proc/net/arp for other hosts, so the
 * MAC address - and with it the OUI vendor database - is frequently unavailable.
 * This object identifies devices from everything we *do* see: DNS/mDNS names,
 * UPnP banners, HTTP Server headers and page titles, service types and open ports.
 *
 * It is the single source of truth used by RobustLanScanner, DiscoveryCoordinator
 * and LanViewModel.
 */
object DeviceIdentifier {

    const val CAMERA = "CAMERA"
    const val ROUTER = "ROUTER"
    const val PHONE = "PHONE"
    const val PRINTER = "PRINTER"
    const val NAS = "NAS"
    const val TV = "TV"
    const val IOT = "IOT"
    const val COMPUTER = "COMPUTER"
    const val UNKNOWN = "UNKNOWN"

    private val CAMERA_PORTS = intArrayOf(554, 8554, 8899, 37777, 34567)
    private val PRINTER_PORTS = intArrayOf(515, 631, 9100)
    private val NAS_PORTS = intArrayOf(5000, 5001, 548, 2049)
    private val COMPUTER_PORTS = intArrayOf(135, 139, 445, 3389)

    private val CAMERA_VENDORS = listOf(
        "hikvision", "dahua", "axis", "reolink", "foscam", "amcrest", "ezviz",
        "uniview", "annke", "swann", "lorex", "wyze", "arlo", "xmei", "icsee", "vstarcam"
    )

    private val PHONE_VENDORS = listOf(
        "apple", "samsung", "google", "xiaomi", "huawei", "oneplus", "oppo", "vivo",
        "realme", "motorola", "nokia", "honor"
    )

    private val TV_VENDORS = listOf(
        "sony", "lg", "tcl", "hisense", "vizio", "roku", "sharp", "panasonic", "philips"
    )

    private val IOT_VENDORS = listOf(
        "espressif", "shelly", "sonoff", "tuya", "tasmota", "home assistant", "lifx",
        "ecobee", "honeywell", "irobot", "roborock", "eufy", "aqara", "broadlink", "govee"
    )

    /** Canonical vendor name -> lowercase tokens that identify it. */
    private val VENDORS: List<Pair<String, List<String>>> = listOf(
        "Apple" to listOf("apple", "iphone", "ipad", "ipod", "macbook", "imac", "appletv", "apple tv", "airport", "homepod", "airplay"),
        "Samsung" to listOf("samsung", "galaxy", "sm-", "tizen"),
        "Google" to listOf("google", "nest", "chromecast", "pixel", "gcast"),
        "Amazon" to listOf("amazon", "echo", "alexa", "fire tv", "firetv", "kindle"),
        "Microsoft" to listOf("microsoft", "xbox", "msft"),
        "Sony" to listOf("sony", "playstation", "ps4", "ps5", "bravia"),
        "Nintendo" to listOf("nintendo", "wiiu"),
        "LG" to listOf("lg electronics", "lge", "webos", "lg-"),
        "TCL" to listOf("tcl"),
        "Hisense" to listOf("hisense"),
        "Philips" to listOf("philips", "hue", "signify"),
        "Sonos" to listOf("sonos"),
        "Bose" to listOf("bose"),
        "JBL" to listOf("jbl", "harman"),
        "Roku" to listOf("roku"),
        "Vizio" to listOf("vizio"),
        "Panasonic" to listOf("panasonic", "viera"),
        "Sharp" to listOf("sharp-", "aquos"),
        "Denon" to listOf("denon"),
        "Yamaha" to listOf("yamaha"),
        "Xiaomi" to listOf("xiaomi", "redmi", "mi-", "mijia", "yeelight"),
        "Huawei" to listOf("huawei", "honor"),
        "OnePlus" to listOf("oneplus"),
        "Oppo" to listOf("oppo"),
        "Vivo" to listOf("vivo-"),
        "Realme" to listOf("realme"),
        "Motorola" to listOf("motorola", "moto "),
        "Nokia" to listOf("nokia"),
        "TP-Link" to listOf("tp-link", "tplink", "archer", "deco", "kasa", "omada"),
        "D-Link" to listOf("d-link", "dlink", "dcs-", "dir-"),
        "NETGEAR" to listOf("netgear", "orbi", "nighthawk"),
        "Linksys" to listOf("linksys", "velop"),
        "ASUS" to listOf("asus", "rt-ax", "zenwifi", "rt-ac"),
        "AVM" to listOf("fritz", "avm "),
        "Vodafone" to listOf("vodafone", "ultrahub"),
        "Sky" to listOf("sky hub", "skyhub", "sky-"),
        "BT" to listOf("bt hub", "bthub", "bt-"),
        "Virgin Media" to listOf("virginmedia", "virgin media"),
        "TalkTalk" to listOf("talktalk"),
        "Ubiquiti" to listOf("ubiquiti", "unifi", "uap-", "usw-"),
        "MikroTik" to listOf("mikrotik", "routeros"),
        "ZTE" to listOf("zte"),
        "Technicolor" to listOf("technicolor"),
        "Sagemcom" to listOf("sagemcom"),
        "Arris" to listOf("arris"),
        "Cisco" to listOf("cisco", "meraki"),
        "Aruba" to listOf("aruba"),
        "Dell" to listOf("dell", "optiplex", "latitude"),
        "HP" to listOf("hewlett", "officejet", "laserjet", "deskjet", "envy", "hp-", "hp_"),
        "Lenovo" to listOf("lenovo", "thinkpad", "ideapad"),
        "Acer" to listOf("acer"),
        "Intel" to listOf("intel", "nuc"),
        "Raspberry Pi" to listOf("raspberry", "raspberrypi"),
        "Synology" to listOf("synology", "diskstation"),
        "QNAP" to listOf("qnap"),
        "Western Digital" to listOf("western digital", "mycloud", "wd "),
        "Seagate" to listOf("seagate"),
        "Epson" to listOf("epson"),
        "Canon" to listOf("canon"),
        "Brother" to listOf("brother"),
        "Lexmark" to listOf("lexmark"),
        "Xerox" to listOf("xerox"),
        "Kyocera" to listOf("kyocera"),
        "Hikvision" to listOf("hikvision", "ds-2", "ivms"),
        "Dahua" to listOf("dahua", "ipc-", "dh-"),
        "Axis" to listOf("axis comm", "axis-"),
        "Reolink" to listOf("reolink"),
        "Foscam" to listOf("foscam"),
        "Amcrest" to listOf("amcrest"),
        "EZVIZ" to listOf("ezviz"),
        "Uniview" to listOf("uniview", "unv-"),
        "Annke" to listOf("annke"),
        "Swann" to listOf("swann"),
        "Lorex" to listOf("lorex"),
        "Wyze" to listOf("wyze"),
        "Arlo" to listOf("arlo"),
        "Espressif" to listOf("espressif", "esp32", "esp8266", "esp-"),
        "Shelly" to listOf("shelly"),
        "Sonoff" to listOf("sonoff", "itead"),
        "Tuya" to listOf("tuya", "smartlife", "smart life"),
        "Tasmota" to listOf("tasmota"),
        "Home Assistant" to listOf("homeassistant", "home-assistant", "hassio"),
        "LIFX" to listOf("lifx"),
        "Ecobee" to listOf("ecobee"),
        "Honeywell" to listOf("honeywell"),
        "iRobot" to listOf("irobot", "roomba"),
        "Roborock" to listOf("roborock"),
        "Eufy" to listOf("eufy", "anker"),
        "Aqara" to listOf("aqara", "lumi-"),
        "Broadlink" to listOf("broadlink"),
        "Govee" to listOf("govee"),
        "Garmin" to listOf("garmin"),
        "Tesla" to listOf("tesla")
    )

    /** Match a token inside text. Short tokens must sit on word boundaries. */
    private fun matches(text: String, token: String): Boolean {
        if (token.isEmpty()) return false
        if (token.any { it == '-' || it == ' ' || it == '_' } || token.length >= 4) {
            return text.contains(token)
        }
        var index = text.indexOf(token)
        while (index >= 0) {
            val before = if (index == 0) ' ' else text[index - 1]
            val afterIndex = index + token.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            index = text.indexOf(token, index + 1)
        }
        return false
    }

    /** Vendor inferred from any human-readable string (hostname, mDNS name, banner, title). */
    fun vendorFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val haystack = text.lowercase()
        for ((vendor, tokens) in VENDORS) {
            if (tokens.any { matches(haystack, it) }) return vendor
        }
        return null
    }

    fun vendorFromHostname(hostname: String?): String? = vendorFromText(hostname)

    /** Vendor from fingerprint strings; the Server header is more reliable than the title. */
    fun vendorFromHttp(title: String?, serverHeader: String?): String? =
        vendorFromText(serverHeader) ?: vendorFromText(title)

    /**
     * Turns a raw mDNS/UPnP string into something worth showing as a name.
     * Strips service suffixes, protocol banners and URLs; returns null when the
     * only thing we could show would be the host's own IP.
     */
    fun cleanName(raw: String?, ip: String? = null): String? {
        if (raw.isNullOrBlank()) return null

        var text = raw.trim()
            .replace("\\032", " ")
            .replace("%20", " ")
            .substringBefore("\r")
            .substringBefore("\n")
            .trim()

        // mDNS resolves as "HP OfficeJet (_ipp._tcp.)" or "Name (._http._tcp.)"
        text = text.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").trim()
        text = text.removeSuffix(".").trim()
        // Router/mDNS suffixes are plumbing, not part of the device name.
        listOf(".local", ".lan", ".home", ".localdomain", ".broadband", ".internal").forEach { suffix ->
            if (text.endsWith(suffix, ignoreCase = true)) {
                text = text.dropLast(suffix.length).trim().removeSuffix(".")
            }
        }
        if (text.endsWith("._tcp", ignoreCase = true)) text = text.dropLast(5).trim()
        if (text.endsWith("._udp", ignoreCase = true)) text = text.dropLast(5).trim()
        if (text.startsWith("Server:", ignoreCase = true)) {
            text = text.substringAfter(':').trim()
        }

        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.contains("urn:") || lower.contains("uuid:") || lower.contains("upnp/")) return null
        if (lower.startsWith("http://") || lower.startsWith("https://")) return null
        if (lower.contains("linux/") || lower.contains("darwin/") || lower.contains("windows nt")) return null
        if (lower.startsWith("http/")) return null
        if (lower == "unknown" || lower == "ssdp device" || lower == "localhost") return null

        val looksLikeIp = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(text)
        if (looksLikeIp) return null
        if (ip != null && text.equals(ip, ignoreCase = true)) return null

        if (text.length > 48) text = text.take(48).trim()
        return text.ifBlank { null }
    }

    /**
     * Vendor/model/type hint from SSDP headers. Returns a vendor name when the
     * SERVER header names one, otherwise the UPnP device class ("Router", "TV" ...).
     */
    fun ssdpHint(server: String?, usn: String? = null, location: String? = null): String? {
        val combined = listOfNotNull(server, usn, location)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()
        if (combined.isBlank()) return null

        vendorFromText(server)?.let { return it }

        return when {
            combined.contains("internetgatewaydevice") || combined.contains("wandevicedevice") -> "Router"
            combined.contains("mediarenderer") -> "TV"
            combined.contains("mediaserver") -> "NAS"
            combined.contains("printer") || combined.contains("printbasic") -> "Printer"
            combined.contains("digitalsecuritycamera") || combined.contains(":camera") -> "Camera"
            combined.contains("basic:1") -> "IoT"
            else -> vendorFromText(combined)
        }
    }

    /** True when HTTP title, open ports or vendor suggest a camera. */
    fun isCamera(vendor: String?, title: String?, ports: List<Int>, hint: String? = null): Boolean {
        if (ports.any { it in CAMERA_PORTS }) return true

        val text = listOfNotNull(title, vendor, hint).joinToString(" ").lowercase()
        if (text.contains("_rtsp") || text.contains("_onvif") || text.contains("_axis-video")) return true
        if (text.contains("camera") || text.contains("ipcam") || text.contains("ipc-")) return true
        if (text.contains("nvr") || text.contains("dvr") || text.contains("onvif")) return true
        if (text.contains("p2p") || text.contains("ilnk") || text.contains("xmeye")) return true

        return CAMERA_VENDORS.any { vendor?.lowercase()?.contains(it) == true }
    }

    /**
     * Classifies a host into one of [CAMERA], [ROUTER], [PHONE], [PRINTER], [NAS],
     * [TV], [IOT], [COMPUTER] or [UNKNOWN].
     *
     * @param hint raw source/service string, e.g. "mdns:_ipp._tcp." or "ssdp:Router".
     */
    fun deviceType(vendor: String?, name: String?, ports: List<Int>, hint: String? = null): String {
        val vendorLower = vendor?.lowercase().orEmpty()
        val nameLower = name?.lowercase().orEmpty()
        val hintLower = hint?.lowercase().orEmpty()

        fun hasPort(vararg candidates: Int) = ports.any { p -> candidates.contains(p) }
        fun inName(vararg tokens: String) = tokens.any { nameLower.contains(it) }
        fun inHint(vararg tokens: String) = tokens.any { hintLower.contains(it) }
        fun vendorIs(vararg tokens: String) = tokens.any { vendorLower.contains(it) }

        if (isCamera(vendor, name, ports, hint)) return CAMERA

        if (hasPort(*PRINTER_PORTS) || inHint("_ipp", "_pdl-datastream", "_printer", "printer") ||
            inName("printer", "officejet", "laserjet", "deskjet", "epson", "brother", "xerox", "lexmark") ||
            vendorIs("epson", "brother", "lexmark", "xerox", "kyocera")
        ) return PRINTER

        if (hasPort(*NAS_PORTS) || inHint("_smb", "_afpovertcp", "nas") ||
            inName("nas", "diskstation", "synology", "qnap", "mycloud", "storage") ||
            vendorIs("synology", "qnap", "western digital", "seagate")
        ) return NAS

        if (inHint("router", "gateway", "internetgatewaydevice", "access point") ||
            inName("router", "gateway", "ultrahub", "fritz", "archer", "deco", "orbi", "velop",
                "sky hub", "mesh", "access point", "unifi", "repeater", "extender", "modem") ||
            vendorIs("tp-link", "d-link", "netgear", "linksys", "asus", "ubiquiti", "mikrotik",
                "zte", "technicolor", "sagemcom", "arris", "cisco", "aruba", "avm", "vodafone")
        ) return ROUTER

        if (inHint("_googlecast", "_airplay", "mediarenderer", "tv") || hasPort(8009) ||
            inName("chromecast", "androidtv", "fire tv", "firetv", "appletv", "apple tv", "roku",
                "bravia", "webos", "aquos", "viera", "smarttv", "smart tv", " tv", "tv-", "playstation",
                "xbox", "nintendo") ||
            vendorIs("roku", "vizio", "tcl", "hisense")
        ) return TV

        if (inName("macbook", "imac", "laptop", "notebook", "desktop", "thinkpad", "ideapad", "windows") ||
            hasPort(*COMPUTER_PORTS)
        ) return COMPUTER

        // Smart-home names win over the phone vendor list: Nest/Echo/Sonos devices
        // often share a vendor with phones, and "amazon-..." hostnames carry no type.
        if (inHint("_spotify-connect") ||
            inName("sonos", "homepod", "echo", "alexa", "nest", "google home", "home mini",
                "speaker", "soundbar", "bose", "jbl", "esp", "shelly", "tasmota", "sonoff",
                "tuya", "smart plug", "bulb", "light", "iot", "sensor", "thermostat") ||
            vendorIs(*IOT_VENDORS.toTypedArray()) || vendorIs("sonos", "bose", "jbl", "amazon")
        ) return IOT

        if (inName("iphone", "ipad", "ipod", "android", "galaxy", "pixel", "oneplus", "redmi",
                "smartphone", "phone") ||
            vendorIs(*PHONE_VENDORS.toTypedArray())
        ) return PHONE

        if (hasPort(22, 139, 445)) return COMPUTER

        return UNKNOWN
    }
}
