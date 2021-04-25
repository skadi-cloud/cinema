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

interface Tag {
    val tag: String
    val description: String
}

enum class KernelF(private val buildNumber: Int, private val commit: String) : Tag {
    V4731(4731, "f5286c0"),
    V4726(4726, "e49ca16");

    override val tag: String
        get() = "$buildNumber.$commit"

    override val description: String
        get() = "KernelF: $buildNumber"
}

enum class Fasten(private val fastenRelease: String) : Tag {
    V2021_04_05("2021-04-05");

    override val tag: String
        get() = "fasten-$name"
    override val description: String
        get() = "FASTEN: $fastenRelease"
}

@Suppress("EnumEntryName")
enum class ContainerVersion(val mpsVersion: MPSVersion, myTag: Tag? = null) {
    // Add new versions at the end because of DB serialization of ordinal value
    V2020_3_4731_f5286c0(MPSVersion.V2020_3_3, KernelF.V4731),
    V2020_3_1(MPSVersion.V2020_3_1),
    V2020_2_4726_e49ca16(MPSVersion.V2020_2_3, KernelF.V4726),
    V2020_2_FASTEN(MPSVersion.V2020_2_3, Fasten.V2021_04_05);

    /**
     * Name of the container image used for the playground
     */
    val imageName = if (myTag != null) {
        "${mpsVersion.fullVersion}-${myTag.tag}"
    } else {
        mpsVersion.fullVersion
    }

    /**
     * User facing description shown to the user in various locations
     */
    val description = if (myTag != null) {
        "MPS ${mpsVersion.fullVersion} ${myTag.description}"
    } else {
        "MPS ${mpsVersion.fullVersion}"
    }

    fun isUpgrade(current: ContainerVersion) = this.mpsVersion.isUpgrade(current.mpsVersion)
    fun isDowngrade(current: ContainerVersion) = this.mpsVersion.isDowngrade(current.mpsVersion)
}