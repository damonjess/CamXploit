package com.spyboy.camxploit

sealed class CameraFingerprint(
    val brandName: String,
    val ports: List<Int> = listOf(80, 81, 8080, 8081, 88),
    val paths: List<String> = listOf("/", "/login", "/doc/page/login.asp", "/cgi-bin/hello"),
    val matchers: List<Regex>
) {
    object Hikvision : CameraFingerprint(
        brandName = "Hikvision",
        ports = listOf(80, 8000, 8080),
        paths = listOf("/", "/doc/page/login.asp", "/ISAPI/System/deviceInfo"),
        matchers = listOf(
            "hikvision".toRegex(RegexOption.IGNORE_CASE),
            "webLib".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    object Dahua : CameraFingerprint(
        brandName = "Dahua",
        ports = listOf(80, 37777, 8000),
        paths = listOf("/", "/cgi-bin/login.asp"),
        matchers = listOf(
            "dahua".toRegex(RegexOption.IGNORE_CASE),
            "DNVRS".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    object Axis : CameraFingerprint(
        brandName = "Axis",
        ports = listOf(80, 443),
        matchers = listOf(
            "axis".toRegex(RegexOption.IGNORE_CASE),
            "Basic realm=\"camera\"".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    object GenericDvr : CameraFingerprint(
        brandName = "Generic DVR",
        matchers = listOf(
            "thttpd/2.25b".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    object GenericChineseDvr : CameraFingerprint(
        brandName = "Generic Chinese DVR",
        matchers = listOf(
            "realm=\"DVR\"".toRegex(RegexOption.IGNORE_CASE),
            "realm=\"IPCamera\"".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    object OnvifDevice : CameraFingerprint(
        brandName = "ONVIF device",
        matchers = listOf(
            "gSOAP".toRegex(RegexOption.IGNORE_CASE)
        )
    )

    companion object {
        val all = listOf(
            Hikvision,
            Dahua,
            Axis,
            GenericDvr,
            GenericChineseDvr,
            OnvifDevice
        )
    }
}
