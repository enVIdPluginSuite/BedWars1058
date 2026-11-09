rootProject.name = "BedWars1058"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Same modules as the former Maven reactor. Project names are the directory names.
include(
    "bedwars-api",
    "bedwars-plugin",
    "vipfeatures-stub",
    "resetadapter_aswm",
    "resetadapter_slime",
    "resetadapter_slimepaper",
    "versionsupport_common",
    "versionsupport_1_8_R3",
    "versionsupport_1_12_R1",
    "versionsupport_v1_16_R3",
    "versionsupport_v1_17_R1",
    "versionsupport_v1_18_R2",
    "versionsupport_v1_19_R2",
    "versionsupport_v1_19_R3",
    "versionsupport_v1_20_R1",
    "versionsupport_v1_20_R2",
    "versionsupport_v1_20_R3",
    "versionsupport_v1_20_R4",
    "versionsupport_v1_21_R1",
    "versionsupport_v1_21_R2",
    "versionsupport_v1_21_R3",
    "versionsupport_v1_21_R4",
    "versionsupport_v1_21_R5",
    "versionsupport_v1_21_R6",
    "versionsupport_v1_21_R7",
    "versionsupport_v26_R1"
)

// The sidebar library is built from source (SidebarLib/), it is not downloaded.
val sidebarModules = listOf(
    "sidebar-base", "sidebar-cmn1",
    "sidebar-v1_8_R3", "sidebar-v1_12_R1", "sidebar-v1_16_R3", "sidebar-v1_17_R1", "sidebar-v1_18_R2",
    "sidebar-v1_19_R2", "sidebar-v1_19_R3", "sidebar-v1_20_R1", "sidebar-v1_20_R2", "sidebar-v1_20_R3",
    "sidebar-v1_20_R4", "sidebar-v1_21_R1", "sidebar-v1_21_R2", "sidebar-v1_21_R3"
)
for (module in sidebarModules) {
    include(module)
    project(":$module").projectDir = file("SidebarLib/$module")
}
