package com.spyboy.camxploit.osint

/** Identifies a source shown by the Intel screen. */
enum class IntelSourceId(val label: String) {
    COUNTRY_DIRECTORY("Country directory"),
    OPENTOPIA("Opentopia"),
    GITHUB("GitHub MotionJPEG"),
    MY_CAMERAS("My cameras")
}

enum class SourceHealthStatus {
    IDLE,
    LOADING,
    HEALTHY,
    PARTIAL,
    ERROR
}

data class SourceHealth(
    val status: SourceHealthStatus = SourceHealthStatus.IDLE,
    val message: String = "Not loaded",
    val itemCount: Int = 0,
    val updatedAt: Long? = null
)

enum class CameraVerification(
    val label: String
) {
    UNCHECKED("Unchecked"),
    VERIFYING("Verifying"),
    MJPEG("MJPEG"),
    SNAPSHOT("Snapshot"),
    RTSP("RTSP"),
    WEB("Web page"),
    UNAVAILABLE("Unavailable")
}

data class CameraDiagnostics(
    val source: String = "",
    val effectiveUrl: String = "",
    val contentType: String = "",
    val verification: CameraVerification = CameraVerification.UNCHECKED,
    val checkedAt: Long? = null,
    val message: String = "Not checked"
)
