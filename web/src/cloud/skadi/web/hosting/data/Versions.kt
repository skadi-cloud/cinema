package cloud.skadi.web.hosting.data

enum class MPSVersion(private val year: Int, private val major: Int, patch: Int) {
    V2020_3_3(2020, 3, 3),
    V2020_3_2(2020, 3, 2),
    V2020_3_1(2020, 3, 1),
    V2020_3(2020, 3, 0),
    V2020_2_3(2020, 2, 3),
    V2020_2_2(2020, 2, 2),
    V2020_2_1(2020, 2, 1),
    V2020_2(2020, 2, 0);

    val fullVersion = if (patch != 0) "$year.$major.$patch" else "$year.$major"
    fun isUpgrade(current: MPSVersion) = current.year < this.year || current.major < this.major
    fun isDowngrade(current: MPSVersion) = current.year > this.year || current.major > this.major
}

@Suppress("EnumEntryName")
enum class ContainerVersion(val mpsVersion: MPSVersion, val buildNumber: Int? = null, val commit: String? = null) {
    V2020_3_4731_f5286c0(MPSVersion.V2020_3_3, 4731, "f5286c0"),
    V2020_3_1(MPSVersion.V2020_3_1),
    V2020_2_4726_e49ca16(MPSVersion.V2020_2_3,4726, "e49ca16");

    val tag =
        if (buildNumber != null && commit != null)
            "${mpsVersion.fullVersion}-$buildNumber.$commit"
        else mpsVersion.fullVersion

    fun isUpgrade(current: ContainerVersion) = this.mpsVersion.isUpgrade(current.mpsVersion)
    fun isDowngrade(current: ContainerVersion) = this.mpsVersion.isDowngrade(current.mpsVersion)
}