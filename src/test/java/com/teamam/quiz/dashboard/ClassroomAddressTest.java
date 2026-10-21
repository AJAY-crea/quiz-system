package com.teamam.quiz.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The QR's default address must be one phones can reach (adapter names as seen on a real laptop). */
class ClassroomAddressTest {

    // Guards against: the QR defaulting to a VirtualBox address that no phone can open (found in testing).
    @Test
    void hidesVirtualMachineAdaptersAndPrefersHotspotThenWifi() {
        assertThat(DashboardController.isVirtualMachineAdapter("VirtualBox Host-Only Ethernet Adapter")).isTrue();
        assertThat(DashboardController.isVirtualMachineAdapter("vEthernet (WSL)")).isTrue();
        assertThat(DashboardController.isVirtualMachineAdapter("Realtek RTL8852BE WiFi 6 802.11ax PCIe Adapter")).isFalse();

        int hotspot = DashboardController.classroomRank("Microsoft Wi-Fi Direct Virtual Adapter #2", "http://192.168.137.1:8080");
        int wifi = DashboardController.classroomRank("Realtek RTL8852BE WiFi 6 802.11ax PCIe Adapter", "http://10.42.83.235:8080");
        int cable = DashboardController.classroomRank("Realtek PCIe GbE Family Controller", "http://10.22.13.61:8080");
        assertThat(hotspot).isLessThan(wifi);
        assertThat(wifi).isLessThan(cable);
    }
}
